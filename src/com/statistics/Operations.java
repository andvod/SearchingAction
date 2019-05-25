package com.statistics;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;
import javax.script.Invocable;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public class Operations {
	public static final String LINK = "LINK";
	public static final String NAME = "NAME";
	public static final String REVIEWS = "REVIEWS";
	public static final String TITLE = "TITLE";
	public static final String AUTHORS = "AUTHORS";
	public static final String DATE = "DATE";
	
	Lock lock = new ReentrantLock();
	
	Invocable invocable;
	private List<String> links = new ArrayList<String>();
	private boolean downloadFiles = false;
	private String fileName;
	
	public Operations(String fileName) {
		this.fileName = fileName;
		setDownloadFiles(true);
	}
	
	public Operations() {
	}

	public ByteArrayInputStream getStream(String url){
		FileOutputStream fos = null;
		InputStream inputStream = null;
		ByteArrayInputStream bais = null;
		try{
			URL website = new URL(url);
			HttpsURLConnection con = (HttpsURLConnection)website.openConnection();
			con.setRequestProperty ( "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:63.0) Gecko/20100101 Firefox/63.0" );
			
			inputStream = con.getInputStream();
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			IOUtils.copy(inputStream, baos);
			
			byte[] bytes = baos.toByteArray();
			bais = new ByteArrayInputStream(bytes);
			if(isDownloadFiles()){
				ReadableByteChannel rbc = Channels.newChannel(bais);
				fos = new FileOutputStream(fileName);
				fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
				bais.reset();
			}
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally{
			close(fos);
		}
		return bais;
	}
	
	public static void close(Closeable c) {
	     if (c == null) return; 
	     try {
	         c.close();
	     } catch (IOException e) {
	    	 e.printStackTrace();
	     }
	  }
	
	protected void execute(InputStreamReader inputStream){
		String inputLine;
		
		String el_a = "";
		try(BufferedReader bf = new BufferedReader(inputStream)){
	        while ((inputLine = bf.readLine()) != null) {
	        	if(StringUtils.isEmpty(el_a)){
	        		String result = parseForA(inputLine);
		        	if(!StringUtils.isEmpty(result) && !StringUtils.isEmpty(el_a)){
		        		System.out.println(el_a + result);
		        	};
		        	if(StringUtils.isEmpty(result) && !StringUtils.isEmpty(el_a)){
		        		el_a += result;
		        	};
		        	if(!StringUtils.isEmpty(result) && StringUtils.isEmpty(el_a)){
		        		el_a = result;
		        	};
	        	}
	        }
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private String parseForA(String inputLine) {
		Pattern p_start = Pattern.compile("<a[ ]{1,5}href");
        Matcher m_start = p_start.matcher(inputLine);
    	if(m_start.find()){
    		Pattern p_end = Pattern.compile("</a>");
            Matcher m_end = p_end.matcher(inputLine);
			if(m_end.find())	
			{
				while(m_start.start() > m_end.end()){
					m_end.find();
				}
				inputLine.substring(m_start.start(), m_end.end());
				links.add(inputLine.substring(m_start.start(), m_end.end()));
				System.out.println(inputLine.substring(m_start.start(), m_end.end()));
            	return parseForA(inputLine.substring(m_end.end()));
			}
            else	return inputLine.substring(m_start.start());
    	}
    	return null;
	}
	
	public List<String> getHrefs(){
		List<String> hrefs = new ArrayList<String>();
		for(String link : links){
			Document doc = Jsoup.parse(link);
			doc.getElementsByTag("a")
			.parallelStream().filter(elem -> !elem.getElementsByClass("btn-link").isEmpty())
			.filter(elem -> !elem.getElementsByClass("browse-by-list-item-link").isEmpty())
			.forEach(i -> hrefs.add(i.attr("href")));
		}
		return hrefs;
	}
	
	public List<String> getLinksTopic(Document doc){
		return doc.getElementsByTag("a")
		.parallelStream()
		.filter(elem -> !elem.getElementsByClass("btn-link").isEmpty())
		.filter(elem -> !elem.getElementsByClass("browse-by-list-item-link").isEmpty())
		.collect(ArrayList<String>::new, 
				(result, element) -> ((List<String>)result).add(element.attr("href")), 
				(result1, result2) -> ((List<String>)result1).addAll((List<String>)result2));
	}
	
	public List<String> getLinksReview(Document doc){
		return doc.getElementsByClass("search-results-item")
		.parallelStream()
		.filter(elem -> !elem.getElementsByClass("search-results-item-body").isEmpty())
		.filter(elem -> !elem.getElementsByClass("result-title").isEmpty())
		.collect(ArrayList<String>::new, 
				(result, element) -> ((List<String>)result).add(element.getElementsByTag("a").attr("abs:href")),
				(result1, result2) -> ((List<String>)result1).addAll((List<String>)result2));
	}
	
	public Optional<Document> getDocument(String url){
		AtomicInteger attempts = new AtomicInteger(0);
		url = addResultPerPage(url);
		Optional<Document> doc = Optional.empty();
		while (attempts.getAndIncrement() < 10){
			try {
				doc = Optional.of(Jsoup.parse(new URL(url), 100000));
				break;
			} catch (IOException e) {
				try {
					TimeUnit.SECONDS.sleep(10);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
	//			e.printStackTrace();
			}
		}
		return doc;
	}
	
	private String addResultPerPage(String url){
		Pattern p = Pattern.compile("resultPerPage=[0-9]*");
        Matcher m = p.matcher(url);
        if(m.find())	{
        	url = m.replaceAll("resultPerPage=100");
        }
        else if(url.contains("?")){
        	url = url.concat("&resultPerPage=100");
        }
        return url;
	}
	
	public Map<String, Object> getCountReviews(Document doc){
		Map<String, Object> result = new HashMap<String, Object>();
		String reviews = null;
		try {
			reviews = doc.getElementsByClass("search-results")
					.parallelStream()
					.filter(elem -> !elem.getElementsByClass("tabs").isEmpty() && !elem.getElementsByClass("rounded").isEmpty())
					.filter(elem -> !elem.getElementsByAttributeValue("data-type", "review").isEmpty())
					.findFirst().orElseThrow(NoSuchFieldException::new).getElementsByAttributeValue("data-type", "review").first().text();
		} catch (NoSuchFieldException e) {
			e.printStackTrace();
		}
		
		String name = null;
		try {
			name = doc.getElementsByClass("active-facet")
					.parallelStream()
					.filter(elem -> !elem.getElementsByClass("facet-pill").isEmpty() && !elem.getElementsByClass("secondary").isEmpty())
					.findFirst().orElseThrow(NoSuchFieldException::new).getElementsByClass("facet-pill").first()
					.getElementsByTag("a").first().text();
		} catch (NoSuchFieldException e) {
			e.printStackTrace();
		}
		
		result.put(REVIEWS, reviews);
		result.put(NAME, name);
		return result;
	}
	
	public Map<String, Object> getTopicInfo(Document doc){
		Map<String, Object> result = new HashMap<String, Object>();
		String title = null;
		try {
			title = doc.getElementsByClass("publication-header")
					.parallelStream()
					.filter(elem -> !elem.getElementsByClass("publication-title").isEmpty())
					.findFirst().orElseThrow(NoSuchFieldException::new).getElementsByClass("publication-title").first().text();
		} catch (NoSuchFieldException e) {
			e.printStackTrace();
		}
		
		List<String> authors = doc.select(".publication-authors .authors .author")
				.parallelStream()
				.collect(ArrayList<String>::new, 
						(array, element) -> 
				{
					((List<String>)array).add(element.getElementsByTag("a").text()); 
				},
						(result1, result2) -> ((List<String>)result1).addAll((List<String>)result2));
		
		String date = null;
		try {
			date = doc.getElementsByClass("publication-header")
					.parallelStream()
					.filter(elem -> !elem.getElementsByClass("publication-metadata-block").isEmpty())
					.filter(elem -> !elem.getElementsByClass("publish-date").isEmpty())
					.findFirst().orElseThrow(NoSuchFieldException::new).getElementsByClass("publish-date").first()
					.text();
		} catch (NoSuchFieldException e) {
			e.printStackTrace();
		}
		
		result.put(TITLE, title);
		result.put(AUTHORS, authors);
		result.put(DATE, date);
		return result;
	}

	public boolean isDownloadFiles() {
		return downloadFiles;
	}

	public void setDownloadFiles(boolean downloadFiles) {
		this.downloadFiles = downloadFiles;
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}
	
	public List<Map<String, Object>> getStatisticsInParalell(List<String> links, String level, int nThreads) {
		ForkJoinPool forkJoinPool = new ForkJoinPool(nThreads);
	    forkJoinPool.awaitQuiescence(2, TimeUnit.SECONDS);
	    List<Map<String, Object>> result = forkJoinPool.invoke(new CountReviews(links, 0, links.size(), level));
	    return result;
	}
	
	public List<Map<String, Object>> getStatisticsInParalell(List<String> links, String level) {
		int nThreads = Runtime.getRuntime().availableProcessors()-1;
		return getStatisticsInParalell(links, level, nThreads);
	}
	
	class CountReviews extends RecursiveTask<List<Map<String, Object>>>{
		public static final String TOPIC = "TOPIC";
		public static final String REVIEW = "REVIEW";
		
		private List<String> links;
		int low;
	    int high;
	    String level;
		
		CountReviews(List<String> links, int low, int high, final String level){
			this.links = links;
			this.low =low;
			this.high = high;
			this.level = level;
		}
		
		private AtomicReference<Map<String, Object>> levelTopic(){
			AtomicReference<Map<String, Object>> ref = new AtomicReference<Map<String, Object>>();
			lock.lock();
			
			Map<String, Object> map = null;
			
			Optional<Document> doc = getDocument(links.get(low));
			if( doc.isPresent()){
				map = getCountReviews(doc.get());
				
				Operations underPage = new Operations();
				List<String> reviewsLinks = underPage.getLinksReview(doc.get());
				List<Map<String, Object>> reviewsInfo = underPage.getStatisticsInParalell(reviewsLinks, Operations.CountReviews.REVIEW);
				writeToFileReview(reviewsInfo, (String)map.get(NAME), "cochrane_reviews.txt");
				System.out.println((String)map.get(NAME) + " is written to file");
			}	else{
				map = new HashMap<String, Object>();
			}
			map.put(LINK, links.get(low));
			ref.set(map);
			
			lock.unlock();
			
			return ref;
		}
		
		private List<Map<String, Object>> levelReview(){
			try {
				TimeUnit.SECONDS.sleep(2);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
			Map<String, Object> map = null;
			
			Optional<Document> doc = getDocument(links.get(low));
			if( doc.isPresent()){
				map = getTopicInfo(doc.get());
			}	else{
				map = new HashMap<String, Object>();
			}
			map.put(LINK, links.get(low));
			list.add(map);
			
			return list;
		}

		@Override
		protected List<Map<String, Object>> compute() {
			if (high - low <= 0 ){
				throw new IndexOutOfBoundsException();
			}
			if (high - low == 1){
				switch(this.level){
					case TOPIC:
						List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
						list.add(levelTopic().get());
						return list;
					case REVIEW:
						return levelReview();
					default:
						throw new IndexOutOfBoundsException("level unrecognized");
				}
			}	
			else{
				int mid = low + (high - low) / 2;
				CountReviews left  = new CountReviews(links, low, mid, level);
				CountReviews right = new CountReviews(links, mid, high, level);
	            left.fork();
	            List<Map<String, Object>> rightResult = right.compute();
	            List<Map<String, Object>> leftResult = left.join();
	            leftResult.addAll(rightResult);
	            return leftResult;
			}
		}
	}

	public static void writeToFileTopic(List<Map<String, Object>> countReviews, String filename) {
		 try(final BufferedWriter bf = new BufferedWriter(new FileWriter(filename, true))){
			 bf.write("\r\n\r\n"); 
	         countReviews.forEach(elem -> {
				try {
					bf.write(elem.get(NAME) + " | " + elem.get(REVIEWS) + " | " + elem.get(LINK) + "\r\n");
				} catch (IOException e) {
					e.printStackTrace();
				}
			});
	     }
		 catch(IOException e){
	    	 System.out.println(e);
	     }
	}
	
	public static void writeToFileReview(List<Map<String, Object>> countReviews, String nameOfTopic, String filename) {
		 try(final BufferedWriter bf = new BufferedWriter(new FileWriter(filename, true))){
			 bf.write("\r\n");
			 countReviews.forEach(elem -> {
				if(elem.containsKey(TITLE) && elem.containsKey(AUTHORS) && elem.containsKey(DATE)){
					try {
						bf.write(elem.get(LINK) + " | " + nameOfTopic + " | " + elem.get(TITLE) + " | " 
							+ ((List<String>)elem.get(AUTHORS)).toString() + " | " + elem.get(DATE) + "\r\n");
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			});
	     }
		 catch(IOException e){
	    	 System.out.println(e);
	     }
	}
	
	public static void writeToFile(String filename) {
		 try(final BufferedWriter bf = new BufferedWriter(new FileWriter(filename))){
			 bf.write("Cochrane reviews\r\n\r\n"); 
	     }
		 catch(IOException e){
	    	 System.out.println(e);
	     }
	}
}
