package students;

import com.mongodb.MongoClient;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Morphia;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

import java.util.List;
import java.util.stream.Collectors;

public class MorphiaTry {
	public static void main(String[] args) {
		MongoClient client = new MongoClient();
		Morphia morphia = new Morphia();
		Datastore datastore = morphia.createDatastore(client, "school");

		List<Student> students = datastore.find(Student.class).asList();
		students.forEach(student -> {
			Double minScore = student.scores.stream()
					.filter(score -> score.type.equals("homework"))
					.map(score -> score.score)
					.min(Double::compareTo)
					.orElse(null);
			if (minScore != null) {
				List<Score> filteredScores = student.scores.stream()
						.filter(score -> !(score.type.equals("homework") && score.score.equals(minScore)))
						.collect(Collectors.toList());
				Query<Student> query = datastore.createQuery(Student.class).field("_id").equal(student._id);
				UpdateOperations<Student> updateStudent = datastore.createUpdateOperations(Student.class).set("scores", filteredScores);
				datastore.update(query, updateStudent);
			}
		});
	}
}
