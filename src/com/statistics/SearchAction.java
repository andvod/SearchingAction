package com.statistics;

import java.io.InputStreamReader;
import java.text.DateFormatSymbols;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.jsoup.nodes.Document;

public class SearchAction {
	public static void main(String[] args) {
		String website = "https://www.cochranelibrary.com/cdsr/reviews/topics";
		
		Operations statistics = new Operations("webpage.html");
		InputStreamReader inputStream = new InputStreamReader(statistics.getStream(website));
//		statistics.execute(inputStream);
//		List<String> hrefs = statistics.getHrefs();
		Operations.close(inputStream);
		
		Optional<Document> doc = statistics.getDocument(website);
		doc.ifPresent(document ->{
			List<String> links = statistics.getLinksTopic(document);
			
			String filename = "cochrane_reviews.txt";
			Operations.writeToFile(filename);
			List<Map<String, Object>> countReviews = statistics.getStatisticsInParalell(links.subList(1, 2), Operations.CountReviews.TOPIC);
	//		for(String link : links){
	//			countReviews.put(link, (statistics.getCountReviews(link)));
	//		}
	
			Operations.writeToFileTopic(countReviews, filename);
		});
	}
}
