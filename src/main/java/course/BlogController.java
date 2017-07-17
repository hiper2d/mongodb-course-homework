/*
 * Copyright 2015 MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package course;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoDatabase;
import freemarker.template.*;
import org.apache.commons.lang3.StringEscapeUtils;
import org.bson.Document;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Morphia;
import spark.Request;
import spark.Response;
import spark.Route;

import javax.servlet.http.Cookie;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static spark.Spark.*;

/**
 * This class encapsulates the controllers for the blog web application.  It delegates all interaction with MongoDB
 * to three Data Access Objects (DAOs).
 * <p/>
 * It is also the entry point into the web application.
 */
public class BlogController {
    private final Configuration cfg;
    private final UserDAO userDAO;
    private final SessionDAO sessionDAO;
    private final BlogPostDAO blogPostDAO;
    private final DefaultObjectWrapper objectWrapper;

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            new BlogController("mongodb://localhost");
        }
        else {
            new BlogController(args[0]);
        }
    }

    public BlogController(String mongoURIString) throws IOException {
        final MongoClient mongoClient = new MongoClient(new MongoClientURI(mongoURIString));
        final MongoDatabase blogDatabase = mongoClient.getDatabase("blog");
        final Morphia morphia = new Morphia();
        final Datastore datastore = morphia.createDatastore(mongoClient, "blog");
        blogPostDAO = new BlogPostDAO(blogDatabase, datastore);
        objectWrapper = new DefaultObjectWrapperBuilder(Configuration.VERSION_2_3_23).build();

        userDAO = new UserDAO(blogDatabase);
        sessionDAO = new SessionDAO(blogDatabase);

        cfg = createFreemarkerConfiguration();
        port(8082);
        initializeRoutes();
    }

    abstract class FreemarkerBasedRoute implements Route {
        final Template template;

        protected FreemarkerBasedRoute(final String templateName) throws IOException {
            template = cfg.getTemplate(templateName);
        }

        @Override
        public Object handle(Request request, Response response) {
            StringWriter writer = new StringWriter();
            try {
                doHandle(request, response, writer);
            } catch (Exception e) {
                e.printStackTrace();
                response.redirect("/internal_error");
            }
            return writer;
        }
        protected abstract void doHandle(final Request request, final Response response, final Writer writer) throws IOException, TemplateException;
    }

    private void initializeRoutes() throws IOException {
        get("/", new FreemarkerBasedRoute("blog_template.ftl") {
            @Override
            public void doHandle(Request request, Response response, Writer writer) throws IOException, TemplateException {
                String username = sessionDAO.findUserNameBySessionId(getSessionCookie(request));

                List<Document> posts = blogPostDAO.findByDateDescending(10);
                HashMap<String, Object> root = new HashMap<>();
                root.put("myposts", posts);
                if (username != null) {
                    root.put("username", username);
                }

                template.process(root, writer);
            }
        });

        post("/signup", new FreemarkerBasedRoute("signup.ftl") {
            @Override
            protected void doHandle(Request request, Response response, Writer writer) throws IOException, TemplateException {
                String email = request.queryParams("email");
                String username = request.queryParams("username");
                String password = request.queryParams("password");
                String verify = request.queryParams("verify");

                HashMap<String, String> root = new HashMap<>();
                root.put("username", StringEscapeUtils.escapeHtml4(username));
                root.put("email", StringEscapeUtils.escapeHtml4(email));

                if (validateSignup(username, password, verify, email, root)) {
                    System.out.println("Signup: Creating user with: " + username + " " + password);
                    if (!userDAO.addUser(username, password, email)) {
                        root.put("username_error", "Username already in use, Please choose another");
                        template.process(root, writer);
                    }
                    else {
                        String sessionID = sessionDAO.startSession(username);
                        System.out.println("Session ID is" + sessionID);

                        response.raw().addCookie(new Cookie("session", sessionID));
                        response.redirect("/welcome");
                    }
                }
                else {
                    System.out.println("User Registration did not validate");
                    template.process(root, writer);
                }
            }
        });

        get("/signup", new FreemarkerBasedRoute("signup.ftl") {
            @Override
            protected void doHandle(Request request, Response response, Writer writer)
                    throws IOException, TemplateException {

                SimpleHash root = new SimpleHash(objectWrapper);

                root.put("username", "");
                root.put("password", "");
                root.put("email", "");
                root.put("password_error", "");
                root.put("username_error", "");
                root.put("email_error", "");
                root.put("verify_error", "");

                template.process(root, writer);
            }
        });

        get("/welcome", new FreemarkerBasedRoute("welcome.ftl") {
            @Override
            protected void doHandle(Request request, Response response, Writer writer) throws IOException, TemplateException {
                String cookie = getSessionCookie(request);
                String username = sessionDAO.findUserNameBySessionId(cookie);
                if (username == null) {
                    System.out.println("welcome() can't identify the user, redirecting to signup");
                    response.redirect("/signup");
                }
                else {
                    SimpleHash root = new SimpleHash(objectWrapper);
                    root.put("username", username);
                    template.process(root, writer);
                }
            }
        });

        // present the login page
        get("/login", new FreemarkerBasedRoute("login.ftl") {
            @Override
            protected void doHandle(Request request, Response response, Writer writer) throws IOException, TemplateException {
                SimpleHash root = new SimpleHash(objectWrapper);
                root.put("username", "");
                root.put("login_error", "");
                template.process(root, writer);
            }
        });

        post("/login", new FreemarkerBasedRoute("login.ftl") {
            @Override
            protected void doHandle(Request request, Response response, Writer writer) throws IOException, TemplateException {
                String username = request.queryParams("username");
                String password = request.queryParams("password");
                System.out.println("Login: User submitted: " + username + "  " + password);

                Document user = userDAO.validateLogin(username, password);
                if (user != null) {
                    String sessionID = sessionDAO.startSession(user.get("_id").toString());
                    if (sessionID == null) {
                        response.redirect("/internal_error");
                    }
                    else {
                        response.raw().addCookie(new Cookie("session", sessionID));
                        response.redirect("/welcome");
                    }
                }
                else {
                    SimpleHash root = new SimpleHash(objectWrapper);
                    root.put("username", StringEscapeUtils.escapeHtml4(username));
                    root.put("password", "");
                    root.put("login_error", "Invalid Login");
                    template.process(root, writer);
                }
            }
        });

        // allows the user to logout of the blog
        get("/logout", new FreemarkerBasedRoute("signup.ftl") {
            @Override
            protected void doHandle(Request request, Response response, Writer writer) throws IOException, TemplateException {
                String sessionID = getSessionCookie(request);
                if (sessionID == null) {
                    // no session to end
                    response.redirect("/login");
                }
                else {
                    // deletes from session table
                    sessionDAO.endSession(sessionID);

                    // this should delete the cookie
                    Cookie c = getSessionCookieActual(request);
                    c.setMaxAge(0);
                    response.raw().addCookie(c);
                    response.redirect("/login");
                }
            }
        });

        get("/post/:permalink", new FreemarkerBasedRoute( "entry_template.ftl") {
            @Override
            protected void doHandle(Request request, Response response, Writer writer) throws IOException, TemplateException {
                String permalink = request.params(":permalink");

                System.out.println("/post: get " + permalink);

                Document post = blogPostDAO.findByPermalink(permalink);
                if (post == null) {
                    response.redirect("/post_not_found");
                }
                else {
                    // empty comment to hold new comment in form at bottom of blog entry detail page
                    SimpleHash newComment = new SimpleHash(objectWrapper);
                    newComment.put("name", "");
                    newComment.put("email", "");
                    newComment.put("body", "");

                    SimpleHash root = new SimpleHash(objectWrapper);

                    root.put("post", post);
                    root.put("comment", newComment);

                    template.process(root, writer);
                }
            }
        });

        // will present the form used to process new blog posts
        get("/newpost", new FreemarkerBasedRoute("newpost_template.ftl") {
            @Override
            protected void doHandle(Request request, Response response, Writer writer) throws IOException, TemplateException {
                String username = sessionDAO.findUserNameBySessionId(getSessionCookie(request));
                if (username == null) {
                    response.redirect("/login");
                } else {
                    SimpleHash root = new SimpleHash(objectWrapper);
                    root.put("username", username);
                    template.process(root, writer);
                }
            }
        });

        post("/newpost", new FreemarkerBasedRoute("newpost_template.ftl") {
            @Override
            protected void doHandle(Request request, Response response, Writer writer)
                    throws IOException, TemplateException {
                String title = StringEscapeUtils.escapeHtml4(request.queryParams("subject"));
                String post = StringEscapeUtils.escapeHtml4(request.queryParams("body"));
                String tags = StringEscapeUtils.escapeHtml4(request.queryParams("tags"));

                String username = sessionDAO.findUserNameBySessionId(getSessionCookie(request));
                if (username == null) {
                    response.redirect("/login");
                }
                else if (title.equals("") || post.equals("")) {
                    HashMap<String, String> root = new HashMap<String, String>();
                    root.put("errors", "post must contain a title and blog entry.");
                    root.put("subject", title);
                    root.put("username", username);
                    root.put("tags", tags);
                    root.put("body", post);
                    template.process(root, writer);
                }
                else {
                    ArrayList<String> tagsArray = extractTags(tags);
                    post = post.replaceAll("\\r?\\n", "<p>");
                    String permalink = blogPostDAO.addPost(title, post, tagsArray, username);
                    response.redirect("/post/" + permalink);
                }
            }
        });

        post("/newcomment", new FreemarkerBasedRoute("entry_template.ftl") {
            @Override
            protected void doHandle(Request request, Response response, Writer writer)
                    throws IOException, TemplateException {
                String name = StringEscapeUtils.escapeHtml4(request.queryParams("commentName"));
                String email = StringEscapeUtils.escapeHtml4(request.queryParams("commentEmail"));
                String body = StringEscapeUtils.escapeHtml4(request.queryParams("commentBody"));
                String permalink = request.queryParams("permalink");

                Document post = blogPostDAO.findByPermalink(permalink);
                if (post == null) {
                    response.redirect("/post_not_found");
                }
                else if (name.equals("") || body.equals("")) {
                    SimpleHash root = new SimpleHash(objectWrapper);
                    SimpleHash comment = new SimpleHash(objectWrapper);
                    comment.put("name", name);
                    comment.put("email", email);
                    comment.put("body", body);
                    root.put("comment", comment);
                    root.put("post", post);
                    root.put("errors", "Post must contain your name and an actual comment");

                    template.process(root, writer);
                } else {
                    blogPostDAO.addPostComment(name, email, body, permalink);
                    response.redirect("/post/" + permalink);
                }
            }
        });

        get("/tag/:thetag", new FreemarkerBasedRoute( "blog_template.ftl") {
            @Override
            protected void doHandle(Request request, Response response, Writer writer)
                    throws IOException, TemplateException {
                String username = sessionDAO.findUserNameBySessionId(getSessionCookie(request));
                SimpleHash root = new SimpleHash(objectWrapper);
                String tag = StringEscapeUtils.escapeHtml4(request.params(":thetag"));
                List<Document> posts = blogPostDAO.findByTagDateDescending(tag);

                root.put("myposts", posts);
                if (username != null) {
                    root.put("username", username);
                }
                template.process(root, writer);
            }
        });

        post("/like", new FreemarkerBasedRoute("entry_template.ftl") {
            @Override
            protected void doHandle(Request request, Response response, Writer writer) throws IOException, TemplateException {
                String permalink = request.queryParams("permalink");
                String commentOrdinalStr = request.queryParams("comment_ordinal");

                int ordinal = Integer.parseInt(commentOrdinalStr);

                // TODO: check return or have checkSession throw
                String username = sessionDAO.findUserNameBySessionId(getSessionCookie(request));
                Document post = blogPostDAO.findByPermalink(permalink);

                if (post == null) {
                    response.redirect("/post_not_found");
                }
                else {
                    blogPostDAO.likePost(permalink, ordinal);
                    response.redirect("/post/" + permalink);
                }
            }
        });

        // used to process internal errors
        get("/internal_error", new FreemarkerBasedRoute("error_template.ftl") {
            @Override
            protected void doHandle(Request request, Response response, Writer writer) throws IOException, TemplateException {
                SimpleHash root = new SimpleHash(objectWrapper);

                root.put("error", "System has encountered an error.");
                template.process(root, writer);
            }
        });
    }

    // helper function to get session cookie as string
    private String getSessionCookie(final Request request) {
        if (request.raw().getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.raw().getCookies()) {
            if (cookie.getName().equals("session")) {
                return cookie.getValue();
            }
        }
        return null;
    }

    // helper function to get session cookie as string
    private Cookie getSessionCookieActual(final Request request) {
        if (request.raw().getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.raw().getCookies()) {
            if (cookie.getName().equals("session")) {
                return cookie;
            }
        }
        return null;
    }

    // tags the tags string and put it into an array
    private ArrayList<String> extractTags(String tags) {

        // probably more efficent ways to do this.
        //
        // whitespace = re.compile('\s')

        tags = tags.replaceAll("\\s", "");
        String tagArray[] = tags.split(",");

        // let's clean it up, removing the empty string and removing dups
        ArrayList<String> cleaned = new ArrayList<>();
        for (String tag : tagArray) {
            if (!tag.equals("") && !cleaned.contains(tag)) {
                cleaned.add(tag);
            }
        }

        return cleaned;
    }

    // validates that the registration form has been filled out right and username conforms
    public boolean validateSignup(String username, String password, String verify, String email,
                                  HashMap<String, String> errors) {
        String USER_RE = "^[a-zA-Z0-9_-]{3,20}$";
        String PASS_RE = "^.{3,20}$";
        String EMAIL_RE = "^[\\S]+@[\\S]+\\.[\\S]+$";

        errors.put("username_error", "");
        errors.put("password_error", "");
        errors.put("verify_error", "");
        errors.put("email_error", "");

        if (!username.matches(USER_RE)) {
            errors.put("username_error", "invalid username. try just letters and numbers");
            return false;
        }

        if (!password.matches(PASS_RE)) {
            errors.put("password_error", "invalid password.");
            return false;
        }


        if (!password.equals(verify)) {
            errors.put("verify_error", "password must match");
            return false;
        }

        if (!email.equals("")) {
            if (!email.matches(EMAIL_RE)) {
                errors.put("email_error", "Invalid Email Address");
                return false;
            }
        }

        return true;
    }

    private Configuration createFreemarkerConfiguration() {
        Configuration retVal = new Configuration(Configuration.VERSION_2_3_23);
        retVal.setClassForTemplateLoading(BlogController.class, "/freemarker");
        return retVal;
    }
}
