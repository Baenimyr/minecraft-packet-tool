package McForgeMods.commandes;

import McForgeMods.ForgeMods;
import McForgeMods.depot.DepotDistant;
import McForgeMods.depot.DepotLocal;
import McForgeMods.outils.Sources;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.json.JSONException;
import picocli.CommandLine;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "update", resourceBundle = "mcforgemods/lang/Update")
public class CommandeUpdate implements Callable<Integer> {
	@CommandLine.Mixin
	ForgeMods.Help help;
	@CommandLine.Option(names = {"-d", "--depot"})
	Path           adresseDepot;
	@CommandLine.Option(names = {"-f", "--from"}, arity = "0..n", descriptionKey = "from")
	List<String>   urlDistant;
	@CommandLine.Option(names = "--no-clear", negatable = true, defaultValue = "true", descriptionKey = "clear")
	boolean        clear;
	
	@Override
	public Integer call() throws Exception {
		final DepotLocal depotLocal = new DepotLocal(adresseDepot);
		try {
			depotLocal.importation();
			if (clear) depotLocal.clear();
		} catch (IOException | JSONException e) {
			System.err.println("Erreur de chargement du dépot local: " + e.getClass() + " " + e.getMessage());
			return 1;
		}
		
		Sources sources;
		if (urlDistant == null) {
			urlDistant = new LinkedList<>();
			final File fichier = depotLocal.dossier.resolve("sources.txt").toFile();
			if (fichier.exists()) {
				try (FileInputStream input = new FileInputStream(fichier)) {
					sources = new Sources(input);
				} catch (IOException ignored) {
					return -1;
				}
			} else {
				System.err.println("Aucune sources disponible.");
				return 2;
			}
		} else {
			sources = new Sources();
			for (String url : urlDistant) {
				try {
					sources.add(new URL(url));
				} catch (MalformedURLException m) {
					System.err.println(String.format("MalformedURL: '%s'", url));
				}
			}
		}
		
		int i = 0;
		Map<URL, Sources.SourceType> src = sources.urls();
		for (URL url : src.keySet()) {
			System.out.println(String.format("%d/%d\t%s", ++i, sources.size(), url));
			try {
				if (src.get(url) == Sources.SourceType.TAR) {
					try (InputStream s = url.openStream(); TarArchiveInputStream tar = new TarArchiveInputStream(s)) {
						final Map<String, ByteArrayInputStream> fichiers = new HashMap<>();
						TarArchiveEntry entry;
						while ((entry = tar.getNextTarEntry()) != null) {
							fichiers.put(entry.getName(), new ByteArrayInputStream(tar.readAllBytes()));
						}
						
						depotLocal.synchronisationDepot(new DepotDistant() {
							@Override
							public InputStream fichierIndexDepot() throws IOException {
								return fichiers.get("Mods.json");
							}
							
							@Override
							public InputStream fichierModDepot(String modid) throws IOException {
								return fichiers.get(modid.charAt(0) + "/" + modid + "/" + modid + ".json");
							}
						});
					} catch (FileNotFoundException fnfe) {
						System.err.println("Fichier absent dans l'archive : '" + fnfe.getMessage() + "'");
					}
				} else {
					depotLocal.synchronisationDepot(new DepotDistant() {
						@Override
						public InputStream fichierIndexDepot() throws IOException {
							return new URL(url, "Mods.json").openStream();
						}
						
						@Override
						public InputStream fichierModDepot(String modid) throws IOException {
							return new URL(url, modid.substring(0, 1) + "/" + modid + "/" + modid + ".json")
									.openStream();
						}
					});
				}
			} catch (MalformedURLException u) {
				System.err.println("URL invalide: " + u.getMessage());
			} catch (IOException io) {
				System.err.println("Erreur lecture du dépot distant: " + io.getClass() + " " + io.getMessage());
			}
		}
		
		try {
			depotLocal.sauvegarde();
			System.out.println("Dépot sauvegardé en " + depotLocal.dossier);
		} catch (IOException | JSONException e) {
			System.err.println("Erreur de sauvegarde du dépot local: " + e.getClass() + " " + e.getMessage());
		}
		return 0;
	}
}
