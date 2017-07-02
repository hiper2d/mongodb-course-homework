package grades;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.mongodb.client.model.Sorts.ascending;
import static com.mongodb.client.model.Sorts.orderBy;

public class MongoClientTry {
	public static void main(String[] args) {
		MongoClient client = new MongoClient();
		MongoDatabase database = client.getDatabase("students");
		final MongoCollection<Document> grades = database.getCollection("grades");

		Bson sort = orderBy(ascending("student_id"), ascending("score"));
		List<Document> toDelete = grades.find(new Document("type", "homework")).sort(sort).into(new ArrayList<>());
		toDelete.forEach(document -> System.out.println(document.toJson()));

		Integer prevStudentId = getStudent_id(toDelete.get(0));
		List<Document> sorted = new ArrayList<>();
		sorted.add(toDelete.get(0));
		for (Document doc: toDelete) {
			Integer currentStudentId = getStudent_id(doc);
			if (!prevStudentId.equals(currentStudentId)) {
				prevStudentId = currentStudentId;
				sorted.add(doc);
			}
		}

		List<ObjectId> mapped = sorted.stream().map(document -> document.getObjectId("_id")).collect(Collectors.toList());
		System.out.println(grades.deleteMany(Filters.in("_id", mapped)));
	}

	private static Integer getStudent_id(Document doc) {
		return doc.getInteger("student_id");
	}
}
