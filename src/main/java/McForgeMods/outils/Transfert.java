package McForgeMods.outils;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

public class Transfert implements Callable<Long> {
	public final URL  source;
	public final URL  destination;
	protected    long total     = 0;
	protected    long transfere = -1;
	
	public Transfert(URL source, URL destination) {
		super();
		this.source = source;
		this.destination = destination;
	}
	
	public long getTotal() {
		return total;
	}
	
	public long getTransfere() {
		return transfere;
	}
	
	private void ajoutTransfere(long ajout) {
		this.transfere += ajout;
	}
	
	
	/**
	 * Execute le transfert.
	 * <p>
	 * Aucune erreur n'est interceptée pour laisser l'usager en faire ce qu'il veut.
	 *
	 * @return le nombre d'octets transférés, {@code -1} si le protocol n'est pas supporté.
	 */
	@Override
	public Long call() throws IOException, URISyntaxException {
		if (source.getProtocol().equals("file") && destination.getProtocol().equals("file")) {
			this.total = Files.size(Path.of(source.toURI()));
			this.transfere = Transfert.transfertFichierFichier(Path.of(source.toURI()), Path.of(destination.toURI()));
		} else {
			try (final InputStream input = openInputStream(source);
				 final OutputStream output = openOutputStream(destination)) {
				if (input == null) {
					return -1L;
				} else if (output == null) {
					return -1L;
				} else {
					int read;
					for (byte[] buffer = new byte[8192];
						 (read = input.read(buffer, 0, 8192)) >= 0; this.ajoutTransfere(read)) {
						output.write(buffer, 0, read);
					}
				}
			}
		}
		return transfere;
	}
	
	private InputStream openInputStream(URL source) throws URISyntaxException, FileNotFoundException, IOException {
		if (source.getProtocol().equals("file")) {
			final Path path = Path.of(source.toURI());
			this.total = Files.size(path);
			return new FileInputStream(path.toFile());
		} else if (source.getProtocol().equals("http") || source.getProtocol().equals("https")) {
			HttpURLConnection connexion = (HttpURLConnection) source.openConnection();
			connexion.setRequestMethod("GET");
			connexion.setRequestProperty("User-Agent",
					"Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");
			
			connexion.connect();
			if (connexion.getResponseCode() == 301 || connexion.getResponseCode() == 302
					|| connexion.getResponseCode() == 307 || connexion.getResponseCode() == 308) {
				String location = connexion.getHeaderField("location");
				return openInputStream(new URL(location));
			}
			
			this.total = connexion.getContentLengthLong();
			return connexion.getInputStream();
		} else {
			return null;
		}
	}
	
	private OutputStream openOutputStream(URL destination) throws URISyntaxException, FileNotFoundException {
		if (source.getProtocol().equals("file")) {
			final File fichier = Path.of(destination.toURI()).toFile();
			if (fichier.getParentFile().exists() || fichier.getParentFile().mkdirs())
				return new FileOutputStream(fichier);
			else return null;
		} else return null;
	}
	
	public static long transfertFichierFichier(Path source, Path destination) throws IOException {
		if (destination.getParent().toFile().exists() || destination.getParent().toFile().mkdirs()) {
			Files.copy(source, destination);
			return Files.size(source);
		}
		return -1;
	}
}
