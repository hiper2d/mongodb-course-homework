package model;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.types.ObjectId;
import org.mongodb.morphia.annotations.Embedded;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity(value = "posts", noClassnameStored = true)
public class Post {
	@Id
	private ObjectId _id;
	private String author;
	private String body;
	private String permalink;
	private List<String> tags;
	@Embedded
	private List<Comment> comments;
	private LocalDateTime date;
	private String title;
}
