package McForgeMods.commandes;

import McForgeMods.ForgeMods;
import McForgeMods.ModVersion;
import McForgeMods.VersionIntervalle;
import McForgeMods.depot.ArbreDependance;
import McForgeMods.depot.DepotInstallation;
import McForgeMods.depot.DepotLocal;
import org.json.JSONException;
import picocli.CommandLine;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * La fonction depot est responsable de toutes les interactions de modification du depot. Elle est le seule capable de
 * modifier les données.
 * <p>
 * Elle permet la lecture/ecriture des attributs des informations sauvegardées dans le dépot. La sous-commande
 * <i>import</i> associé avec une installation minecraft récupère les informations directement dans les
 * <i>mcmod.info</i> des fichiers jar trouvés.
 */
@CommandLine.Command(name = "depot", subcommands = {CommandeDepot.importation.class, CommandeUpdate.class},
		resourceBundle = "mcforgemods/lang/Depot")
public class CommandeDepot implements Runnable {
	
	@CommandLine.Option(names = {"--help"}, usageHelp = true)
	boolean help;
	
	@CommandLine.Spec
	CommandLine.Model.CommandSpec spec;
	
	@Override
	public void run() {
		throw new CommandLine.ParameterException(spec.commandLine(), "Missing required subcommand");
	}
	
	@CommandLine.Command(name = "refresh")
	public int refresh(@CommandLine.Option(names = "--depot") Path chemin_depot, @CommandLine.Mixin ForgeMods.Help help,
			@CommandLine.Option(names = {"-f", "--force"}, defaultValue = "false", descriptionKey = "force")
					boolean force,
			@CommandLine.Option(names = {"-v", "--verbose"}, defaultValue = "false", descriptionKey = "verbose")
					boolean verbose) {
		DepotLocal depot = new DepotLocal(chemin_depot);
		try {
			depot.importation();
		} catch (IOException | JSONException | IllegalArgumentException i) {
			System.err.println("Erreur de lecture du dépot: " + i.getClass().getSimpleName() + ": " + i.getMessage());
			if (!force) return 1;
		}
		
		System.out.println(
				String.format("Dépot importé de %s: %d versions disponibles", depot.dossier, depot.sizeModVersion()));
		
		if (verbose) {
			ArrayList<String> modids = new ArrayList<>(depot.getModids());
			modids.sort(String::compareTo);
			for (String modid : modids) {
				for (ModVersion version : depot.getModVersions(modid)) {
					if (version.urls.isEmpty()) {
						System.out
								.println(String.format("%s: aucun urls de téléchargement", version.toStringStandard()));
					}
				}
			}
			
			for (final String modid : modids) {
				for (final ModVersion version : depot.getModVersions(modid)) {
					ArbreDependance dependances = new ArbreDependance(Collections.singleton(version));
					dependances.extension(depot);
					for (Map.Entry<String, VersionIntervalle> dep : dependances.requis().entrySet()) {
						if (!dep.getKey().equals("forge")) {
							if (!depot.contains(dep.getKey()) || depot.getModVersions(dep.getKey()).stream().noneMatch(
									v -> dep.getValue().equals(VersionIntervalle.ouvert()) || dep.getValue()
											.correspond(v.version))) {
								System.out.println(String.format(
										"'%s' a besoin de '%s@%s', mais il n'est pas disponible dans le dépôt !",
										version.toStringStandard(), dep.getKey(), dep.getValue()));
							}
						}
					}
				}
			}
		}
		
		try {
			depot.sauvegarde();
		} catch (IOException i) {
			System.err.println("Erreur d'écriture du dépot: " + i.getClass().getSimpleName() + ": " + i.getMessage());
			return 1;
		}
		
		System.out.println("Dépot sauvegardé.");
		return 0;
	}
	
	@CommandLine.Command(name = "import")
	static class importation implements Callable<Integer> {
		@CommandLine.Parameters(index = "0", arity = "0..*", paramLabel = "modid", descriptionKey = "modids")
		String[] modids;
		@CommandLine.Option(names = {"-a", "--all"}, defaultValue = "false")
		boolean  all;
		@CommandLine.Option(names = {"-i", "--include-files"}, defaultValue = "false", descriptionKey = "include")
		boolean  include_file;
		
		@CommandLine.Mixin
		ForgeMods.DossiersOptions dossiers;
		@CommandLine.Mixin
		ForgeMods.Help            help;
		
		public Integer call() {
			DepotLocal depot = new DepotLocal(dossiers.depot);
			try {
				depot.importation();
			} catch (IOException | JSONException | IllegalArgumentException i) {
				System.err
						.println("Erreur de lecture des informations du dépot: " + i.getClass().getSimpleName() + " " + i.getMessage());
				return 1;
			}
			DepotInstallation installation = new DepotInstallation(dossiers.minecraft);
			installation.analyseDossier(depot);
			
			System.out.println(
					String.format("%d mods chargés depuis '%s'.", installation.sizeModVersion(), installation.dossier));
			
			final Collection<ModVersion> importation = new ArrayList<>();
			if (all) {
				for (String modid : installation.getModids()) {
					importation.addAll(installation.getModVersions(modid));
				}
			} else if (modids != null && modids.length > 0) {
				for (String modid : modids) {
					if (installation.contains(modid)) {
						importation.addAll(installation.getModVersions(modid));
					} else {
						System.err.println("Modid non reconnu: '" + modid + "'");
					}
				}
			} else {
				System.err.println("Il faut au moins un nom de mod à importer. Sinon utiliser l'option '--all'.");
				return 2;
			}
			
			for (ModVersion version : importation) {
				final ModVersion reelle = depot.ajoutModVersion(
						new ModVersion(depot.ajoutMod(version.mod), version.version, version.mcversion));
				reelle.fusion(version);
				reelle.urls.removeIf(url -> url.getProtocol().equals("file"));
				
				if (include_file) {
					copieFichier(depot, version);
				}
			}
			System.out.println(String.format("%d versions importées.", importation.size()));
			
			try {
				depot.sauvegarde();
			} catch (IOException i) {
				System.err.println(
						String.format("Impossible de sauvegarder le dépot local à '%s': %s %s", depot.dossier,
								getClass().getSimpleName(), i.getMessage()));
				return 1;
			}
			return 0;
		}
		
		private void copieFichier(final DepotLocal depot, ModVersion version) {
			Optional<URL> fichier = version.urls.stream().filter(u -> u.getProtocol().equals("file")).findFirst();
			if (fichier.isPresent()) {
				try {
					final Path source = Path.of(fichier.get().toURI());
					final Path destination = depot.dossierCache(version).resolve(source.getFileName());
					destination.getParent().toFile().mkdirs();
					Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
					version.ajoutURL(destination.toUri().toURL());
				} catch (IOException | URISyntaxException e) {
					System.err.println(String.format("Impossible de copier le fichier '%s'!", fichier.get()));
				}
			} else {
				System.err.println(
						String.format("Aucun fichier à copier pour le mod %s=%s", version.mod.modid, version.version));
			}
		}
	}
}
