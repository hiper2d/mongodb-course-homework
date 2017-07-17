package course;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import model.Comment;
import model.Post;
import org.bson.Document;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Key;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

import java.util.*;
import java.util.function.Consumer;

public class BlogPostDAO {
	final MongoCollection<Document> postsCollection;
	final Datastore datastore;

	public BlogPostDAO(final MongoDatabase blogDatabase, final Datastore datastore) {
		postsCollection = blogDatabase.getCollection("posts");
		this.datastore = datastore;
	}

	public Document findByPermalink(String permalink) {
		Document post = postsCollection
				.find(Filters.eq("permalink", permalink))
				.first();
		if (post != null) {
			List<Document> comments = (List<Document>) post.get("comments");
			for (Document comment : comments) {
				if (!comment.containsKey("num_likes")) {
					comment.put("num_likes", 0);
				}
			}
		}
		return post;
	}

	public List<Document> findByDateDescending(int limit) {
		return postsCollection.find()
				.sort(Sorts.descending("date"))
				.limit(limit)
				.into(new ArrayList<>());
	}

	public List<Document> findByTagDateDescending(final String tag) {
		return postsCollection.find(Filters.eq("tags", tag))
				.sort(Sorts.descending("date"))
				.limit(10)
				.into(new ArrayList<>());
	}

	public String addPost(String title, String body, List tags, String username) {

		System.out.println("inserting blog entry " + title + " " + body);

		String permalink = title.replaceAll("\\s", "_"); // whitespace becomes _
		permalink = permalink.replaceAll("\\W", ""); // get rid of non alphanumeric
		permalink = permalink.toLowerCase();

		String permLinkExtra = String.valueOf(GregorianCalendar
				.getInstance().getTimeInMillis());
		permalink += permLinkExtra;

		Document post = new Document();
		post.append("author", username)
				.append("body", body)
				.append("permalink", permalink)
				.append("tags", tags)
				.append("comments", Collections.emptyList())
				.append("date", new Date())
				.append("title", title);

		try {
			postsCollection.insertOne(post);
			System.out.println("Inserting blog post with permalink " + permalink);
		} catch (Exception e) {
			System.out.println("Error inserting post");
			return null;
		}

		return permalink;
	}

	public void addPostComment(final String name, final String email, final String body, final String permalink) {
		Consumer<Post> commentsUpdater = (Post post) -> {
			List<Comment> comments = getComments(post);
			Comment comment = new Comment();
			comment.setBody(body);
			if (name != null) {
				comment.setAuthor(name);
			}
			if (email != null) {
				comment.setEmail(email);
			}
			comments.add(comment);
		};
		updateComments(permalink, commentsUpdater);
	}

	public void likePost(final String permalink, final int ordinal) {
		Consumer<Post> commentsUpdater = (Post post) -> {
			List<Comment> comments = getComments(post);
			if (comments.size() < ordinal + 1) {
				throw new RuntimeException("Ooops, that's weird. Ordinal is greated then the comments amount.");
			}
			int oldNum = post.getComments().get(ordinal).getNum_likes();
			post.getComments().get(ordinal).setNum_likes(++oldNum);
		};
		updateComments(permalink, commentsUpdater);
	}

	private List<Comment> getComments(Post post) {
		List<Comment> comments = post.getComments();
		if (comments == null) {
			comments = new ArrayList<>();
		}
		return comments;
	}

	private void updateComments(String permalink, Consumer<Post> commentsUpdater) {
		Query<Post> query = datastore.createQuery(Post.class).field("permalink").equal(permalink);
		Post post = query.get();
		commentsUpdater.accept(post);
		UpdateOperations<Post> updateComments = datastore
				.createUpdateOperations(Post.class)
				.set("comments", post.getComments());
		datastore.update(query, updateComments);
	}
}
