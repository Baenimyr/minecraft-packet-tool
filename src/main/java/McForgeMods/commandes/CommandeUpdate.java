package McForgeMods.commandes;

import McForgeMods.ForgeMods;
import McForgeMods.depot.DepotLocal;
import McForgeMods.outils.Sources;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.VFS;
import org.json.JSONException;
import picocli.CommandLine;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystemException;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
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
		
		final Sources sources = new Sources();
		if (urlDistant == null) {
			urlDistant = new LinkedList<>();
			final File fichier = depotLocal.dossier.resolve("sources.txt").toFile();
			if (fichier.exists()) {
				try (FileInputStream input = new FileInputStream(fichier)) {
					sources.importation(input);
				} catch (IOException ignored) {
					return -1;
				}
			} else {
				System.err.println("Aucune sources disponible.");
				return 2;
			}
		} else {
			for (String url : urlDistant) {
				try {
					sources.add(new URI(url));
				} catch (URISyntaxException m) {
					System.err.printf("MalformedURL: '%s'%n", url);
				}
			}
		}
		
		int i = 0;
		final FileSystemManager filesystem = VFS.getManager();
		for (URI uri : sources.urls()) {
			System.out.printf("%d/%d\t%s%n", ++i, sources.size(), uri);
			try {
				FileObject mods = filesystem.resolveFile(uri.resolve(DepotLocal.MODS));
				
				if (mods.exists()) {
					try (InputStream is = mods.getContent().getInputStream()) {
						depotLocal.synchronisationDepot(is);
					}
				} else {
					System.err.printf("Impossible de lire %s à %s !%n", DepotLocal.MODS, uri);
				}
			} catch (FileSystemException io) {
				System.err.println("Erreur lecture du dépot distant: " + io.getClass() + " " + io.getMessage());
				io.printStackTrace();
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
