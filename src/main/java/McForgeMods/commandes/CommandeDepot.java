package McForgeMods.commandes;

import McForgeMods.ForgeMods;
import McForgeMods.ModVersion;
import McForgeMods.VersionIntervalle;
import McForgeMods.depot.ArbreDependance;
import McForgeMods.depot.ArchiveMod;
import McForgeMods.depot.DepotLocal;
import org.json.JSONException;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
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
 * <i>import</i> associée avec une installation minecraft récupère les informations directement dans les
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
			
			for (final String modid : modids) {
				for (final ModVersion version : depot.getModVersions(modid)) {
					ArbreDependance dependances = new ArbreDependance(depot, Collections.singleton(version));
					dependances.resolution();
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
		List<String> modids;
		@CommandLine.Option(names = {"-a", "--all"}, defaultValue = "false")
		boolean      all;
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
				System.err.println(String.format("Erreur de lecture des informations du dépot: %s %s",
						i.getClass().getSimpleName(), i.getMessage()));
				return 1;
			}
			List<ArchiveMod> archives = ArchiveMod.analyseDossier(dossiers.minecraft, depot);
			
			System.out.println(String.format("%d mods chargés depuis '%s'.", archives.size(), dossiers.minecraft));
			
			final Collection<ArchiveMod> importation = new ArrayList<>();
			if (all) {
				importation.addAll(archives);
			} else if (modids != null && modids.size() > 0) {
				archives.stream().filter(a -> modids.contains(a.mod.modid)).forEach(importation::add);
				for (String modid : modids) {
					final File fichier = new File(modid);
					if (fichier.exists()) {
						try {
							ArchiveMod mod = ArchiveMod.importationJar(fichier);
							if (mod.isPresent()) {
								URL url = fichier.toURI().toURL();
								mod.modVersion.urls.add(url);
								importation.add(mod);
							}
						} catch (IOException i) {
							i.printStackTrace();
						}
					} else if (archives.stream().noneMatch(a -> a.mod.modid.equals(modid))) {
						System.err.println("Mod non reconnu: '" + modid + "'");
					}
				}
			} else {
				System.err.println("Il faut au moins un nom de mod à importer. Sinon utiliser l'option '--all'.");
				return 2;
			}
			
			for (ArchiveMod version_client : importation) {
				depot.getMod(version_client.mod.modid).fusion(version_client.mod);
				final ModVersion reelle = depot.ajoutModVersion(version_client.modVersion);
				reelle.urls.removeIf(url -> url.getProtocol().equals("file"));
				
				if (include_file) {
					copieFichier(depot, version_client.modVersion, version_client.fichier);
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
		
		/**
		 * Copie un fichier jar dans le cache du dépot local.
		 *
		 * @param depot: depot local dont on remplit le cache
		 * @param version: informations sur le mode, utiliser par le cache
		 * @param source: fichier d'origine
		 * @see DepotLocal#dossierCache(ModVersion)
		 */
		private void copieFichier(final DepotLocal depot, final ModVersion version, File source) {
			try {
				final Path destination = depot.dossierCache(version).resolve(source.getName());
				if (destination.getParent().toFile().exists() || destination.getParent().toFile().mkdirs())
					Files.copy(source.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
				else System.err.println(
						String.format("Impossible de créer le dossier '%s'", destination.getParent().toString()));
				
				//Optional<ModVersion> version_depot = depot
				//		.getModVersion(depot.getMod(version.modid), version.version);
				//if (version_depot.isPresent()) version_depot.get().ajoutURL(destination.toUri().toURL());
			} catch (IOException e) {
				System.err.println(String.format("Impossible de copier le fichier '%s'!", source));
			}
		}
	}
}
