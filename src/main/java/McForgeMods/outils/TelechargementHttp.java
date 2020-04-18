package McForgeMods.outils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;

/** Permet de télécharger un fichier. */
public class TelechargementHttp {
	public final URL        url;
	public       File       fichier;
	public long taille_totale = 0;
	public long telecharge    = 0;
	private      HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build();
	
	public TelechargementHttp(URL url, File fichier) {
		this.url = url;
		this.fichier = fichier;
	}
	
	/** Envoie une requête HEAD et retourne les en-têtes. */
	public HttpResponse<String> recupereInformations() throws URISyntaxException, IOException, InterruptedException {
		URL url = this.url;
		HttpRequest connexion = HttpRequest.newBuilder().method("HEAD", HttpRequest.BodyPublishers.noBody())
				.uri(url.toURI()).setHeader("User-Agent", "Mozilla/5.0 Firefox/75.0")
				.setHeader("Accept-Language", "en-US").build();
		
		return client.send(connexion, HttpResponse.BodyHandlers.ofString());
	}
	
	public long telechargement() throws URISyntaxException, IOException, InterruptedException {
		final long telecharge_avant = telecharge;
		
		HttpRequest connexion = HttpRequest.newBuilder().GET().uri(url.toURI())
				.setHeader("User-Agent", "Mozilla/5.0 Firefox/75.0").setHeader("Accept-Language", "en-US").build();
		
		HttpResponse<InputStream> response = client.send(connexion, HttpResponse.BodyHandlers.ofInputStream());
		if (response.statusCode() >= 400) {
			throw new IOException(
					String.format("Server sent code %d for '%s'", response.statusCode(), connexion.uri()));
		}
		this.taille_totale = Long.parseLong(response.headers().firstValue("Content-Length").get());
		try (InputStream is = response.body(); FileOutputStream fos = new FileOutputStream(this.fichier)) {
			ReadableByteChannel f_http = Channels.newChannel(is);
			FileChannel f_channel = fos.getChannel();
			
			while (telecharge < taille_totale) telecharge += f_channel.transferFrom(f_http, telecharge,
					taille_totale - telecharge);
			f_http.close();
			f_channel.close();
		}
		
		return telecharge - telecharge_avant;
	}
}
