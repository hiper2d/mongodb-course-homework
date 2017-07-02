package students;

import org.mongodb.morphia.annotations.Embedded;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

import java.util.List;

@Entity(value = "students", noClassnameStored = true)
class Student {
	@Id
	public Long _id;
	public String name;
	@Embedded
	public List<Score> scores;
}
