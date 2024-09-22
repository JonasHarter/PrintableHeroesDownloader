package jh.phdl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.net.URIBuilder;
import org.json.JSONArray;
import org.json.JSONObject;

public class Main {
	// https://github.com/pakoito/printableheroes

	private static String URL_BASE = "https://api.printableheroes.com";
	private final static String URL_GETALL = "/api/minis/getAll";
	private final static String URL_GET = "/api/minifiles/get";
	private final static String URL_GET_P_MINIID = "miniId";
	private final static String URL_FILES = "/files";
	private final static String URL_FILES_P_MINIID = "mini_id";
	private final static String URL_FILES_P_TIER = "tier";
	private final static String URL_FILES_P_FILENAME = "file_name";
	private final static String BEARER_TOKEN_PREFIX = "Bearer ";

//	private static Integer startId = 990;

//	private final static Integer TIER = 3;
//	private static String bearerToken = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1bmlxdWVfbmFtZSI6IjI2ODc2MTUxIiwicm9sZSI6IjMiLCJyZWZyZXNoX3Rva2VuIjoiaWJ4VUFiYVJGdS1sQThmcEVhd2pORXotVlkxcHIzMzRobGRUYllpSnJISSIsIm5iZiI6MTcyNzAwOTQxMSwiZXhwIjoxNzI5NjAxNDExLCJpYXQiOjE3MjcwMDk0MTF9._bGCS9ZciiJK1uep40npbyvHQNaCsVzupIRI5fqV3mM";

	private static Path downloadPath;
	private static Integer configStartId;
	private static Integer configTier;
	private static String configBearerToken;

	public static void main(String[] args) throws Exception {
		// Init
		Path basePath = Paths.get(System.getProperty("user.dir"));
		readConfiguration(basePath.resolve("data/downloader.config"));
		downloadPath = basePath.resolve("download");
		Files.createDirectories(downloadPath);

		// DL stuff
		List<Integer> allIds = getAllIds();
		for (Integer id : allIds) {
			if (id < configStartId)
				continue;
			System.out.println(id);
			List<MiniData> list = getId(id);
			for (MiniData miniData : list) {
				downloadFile(miniData);
			}
		}
		System.out.println("DONE");
	}

	private static void readConfiguration(Path filePath) throws Exception {
		Properties prop = new Properties();
		try (FileInputStream fis = new FileInputStream(filePath.toFile())) {
			prop.load(fis);
		}
		configStartId = Integer.parseInt(prop.getProperty("startId"));
		configTier = Integer.parseInt(prop.getProperty("tier"));
		configBearerToken = BEARER_TOKEN_PREFIX + prop.getProperty("bearerToken");
	}

	private static List<Integer> getAllIds() throws Exception {
		List<Integer> returnList = new ArrayList<>();
		CloseableHttpClient httpclient = HttpClients.createDefault();
		URIBuilder uriBuilder = new URIBuilder(URL_BASE);
		uriBuilder.appendPath(URL_GETALL);
		HttpGet httpGet = new HttpGet(uriBuilder.build().toString());
		HttpClientResponseHandler<String> responseHandler = new HttpClientResponseHandler<String>() {

			@Override
			public String handleResponse(ClassicHttpResponse response) throws HttpException, IOException {
				int status = response.getCode();
				if (status >= 200 && status < 300) {
					HttpEntity entity = response.getEntity();
					return entity != null ? EntityUtils.toString(entity) : null;
				} else {
					throw new ClientProtocolException("Unexpected response status: " + status);
				}
			}
		};
		String responseBody = httpclient.execute(httpGet, responseHandler);
		// Parse
		JSONArray listArray = new JSONArray(responseBody);
		for (int i = 0; i < listArray.length(); i++) {
			JSONObject listObject = listArray.getJSONObject(i);
			Integer id = listObject.getInt("Id");
			returnList.add(id);
		}
		return returnList;
	}

	private static List<MiniData> getId(Integer id) throws Exception {
		List<MiniData> returnList = new ArrayList<>();
		CloseableHttpClient httpclient = HttpClients.createDefault();
		URIBuilder uriBuilder = new URIBuilder(URL_BASE);
		uriBuilder.appendPath(URL_GET);
		uriBuilder.addParameter(URL_GET_P_MINIID, id.toString());
		HttpGet httpGet = new HttpGet(uriBuilder.build().toString());
		HttpClientResponseHandler<String> responseHandler = new HttpClientResponseHandler<String>() {

			@Override
			public String handleResponse(ClassicHttpResponse response) throws HttpException, IOException {
				int status = response.getCode();
				if (status >= 200 && status < 300) {
					HttpEntity entity = response.getEntity();
					return entity != null ? EntityUtils.toString(entity) : null;
				} else {
					throw new ClientProtocolException("Unexpected response status: " + status);
				}
			}
		};
		String responseBody = httpclient.execute(httpGet, responseHandler);
		// Parse
		JSONObject json = new JSONObject(responseBody);
		for (int tier = 0; tier <= configTier; tier++) {
			String tierString = Integer.toString(tier);
			if (!json.has(tierString))
				continue;
			JSONArray tierObject = json.getJSONArray(tierString);
			for (int i = 0; i < tierObject.length(); i++) {
				JSONObject fileObject = tierObject.getJSONObject(i);
				String fileName = fileObject.getString("FileName");
				returnList.add(new MiniData(id, tier, fileName));
			}
		}
		return returnList;
	}

	private static class MiniData {
		private final Integer id;
		private final Integer tier;
		private final String fileName;

		public MiniData(Integer id, Integer tier, String fileName) {
			super();
			this.id = id;
			this.tier = tier;
			this.fileName = fileName;
		}

		@Override
		public String toString() {
			return "MiniData [id=" + id + ", tier=" + tier + ", fileName=" + fileName + "]";
		}

	}

	private static void downloadFile(MiniData miniData) throws Exception {
		Path filePath = downloadPath.resolve(miniData.fileName);
		File file = filePath.toFile();
		if (file.exists())
			return;
		CloseableHttpClient httpclient = HttpClients.createDefault();
		URIBuilder uriBuilder = new URIBuilder(URL_BASE);
		uriBuilder.appendPath(URL_FILES);
		uriBuilder.addParameter(URL_FILES_P_MINIID, miniData.id.toString());
		uriBuilder.addParameter(URL_FILES_P_TIER, miniData.tier.toString());
		uriBuilder.addParameter(URL_FILES_P_FILENAME, miniData.fileName);
		HttpGet httpGet = new HttpGet(uriBuilder.build().toString());
		httpGet.addHeader(new BasicHeader("Authorization", configBearerToken));
		HttpClientResponseHandler<Void> responseHandler = new HttpClientResponseHandler<Void>() {

			@Override
			public Void handleResponse(ClassicHttpResponse response) throws HttpException, IOException {
				int status = response.getCode();
				if (status >= 200 && status < 300) {
					HttpEntity entity = response.getEntity();
					try (FileOutputStream fos = new FileOutputStream(file)) {
						fos.write(entity.getContent().readAllBytes());
					}
				} else {
					throw new ClientProtocolException("Unexpected response status: " + status);
				}
				return null;
			}
		};
		httpclient.execute(httpGet, responseHandler);
	}
}
