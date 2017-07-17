package model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Comment {
	private String author;
	private String email;
	private String body;
	private int num_likes;
}
