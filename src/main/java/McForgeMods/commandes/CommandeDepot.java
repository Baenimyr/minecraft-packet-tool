package McForgeMods.commandes;

import McForgeMods.ForgeMods;
import McForgeMods.Mod;
import McForgeMods.ModVersion;
import McForgeMods.VersionIntervalle;
import McForgeMods.depot.ArbreDependance;
import McForgeMods.depot.DepotDistant;
import McForgeMods.depot.DepotInstallation;
import McForgeMods.depot.DepotLocal;
import McForgeMods.outils.Dossiers;
import McForgeMods.outils.Sources;
import org.json.JSONException;
import picocli.CommandLine;
import tar.FichierTar;

import java.io.*;
import java.net.MalformedURLException;
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
@CommandLine.Command(name = "depot", subcommands = {CommandeDepot.importation.class},
		description = {"Outil de gestion d'un dépot.",
				"Une installation minecraft peut être utilisée comme source de fichiers."})
public class CommandeDepot implements Runnable {
	
	@CommandLine.Option(names = {"--help"}, usageHelp = true)
	boolean help;
	
	@CommandLine.Spec
	CommandLine.Model.CommandSpec spec;
	
	@Override
	public void run() {
		throw new CommandLine.ParameterException(spec.commandLine(), "Missing required subcommand");
	}
	
	@CommandLine.Command(name = "refresh", description = "Importe est sauvegarde le dépot.\nPermet de détecter des "
			+ "erreurs. En cas d'erreur ne sauvegarde pas les informations corrompues.")
	public int refresh(@CommandLine.Mixin ForgeMods.DossiersOptions dossiers, @CommandLine.Mixin ForgeMods.Help help,
			@CommandLine.Option(names = {"-f", "--force"}, defaultValue = "false",
					description = "Force la sauvegarde du dépot, même après des erreurs lors de l'importation.")
					boolean force, @CommandLine.Option(names = {"-v", "--verbose"}, defaultValue = "false",
			description = "Affiche plus d'informations sur les erreurs.") boolean verbose) {
		DepotLocal depot = new DepotLocal(dossiers.depot);
		try {
			depot.importation();
		} catch (IOException | JSONException i) {
			System.err.println("Erreur de lecture du dépot: " + i.getClass() + ": " + i.getMessage());
			if (!force) return 1;
		}
		
		System.out.println(String.format("Dépot importé: %d versions disponibles", depot.sizeModVersion()));
		
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
					ArbreDependance dependances = new ArbreDependance(depot, Collections.singleton(version));
					for (Map.Entry<Mod, VersionIntervalle> dep : dependances.requis().entrySet()) {
						if (!dep.getKey().modid.equals("forge")) {
							if (!depot.contains(dep.getKey()) || depot.getModVersions(dep.getKey()).stream().noneMatch(
									v -> dep.getValue() == VersionIntervalle.ouvert || dep.getValue()
											.correspond(v.version))) {
								System.out.println(String.format(
										"'%s' a besoin de '%s@%s', mais il n'est pas disponible dans le dépôt !",
										version.toStringStandard(), dep.getKey().modid, dep.getValue()));
							}
						}
					}
				}
			}
		}
		
		try {
			depot.sauvegarde();
		} catch (IOException i) {
			System.err.println("Erreur d'écriture du dépot: " + i.getClass() + ": " + i.getMessage());
			return 1;
		}
		
		System.out.println("Dépot sauvegardé.");
		return 0;
	}
	
	@CommandLine.Command(name = "import",
			description = "Permet d'importer des informations présentes dans les fichiers mcmod.info des archives jar. "
					+ "Utilise un dépot minecraft comme source des jars.")
	static class importation implements Callable<Integer> {
		@CommandLine.Parameters(index = "0", arity = "0..*", paramLabel = "modid",
				description = "Liste de mod spécifiques à importer (modid).")
		String[] modids;
		@CommandLine.Option(names = {"-a", "--all"}, defaultValue = "false", description = "Importe tout")
		boolean  all;
		@CommandLine.Option(names = {"--include-files"}, defaultValue = "false",
				description = "Copie les fichiers dans le dépot et ajoute un url relatif pour accéder au fichier."
						+ "Ne copie pas les fichiers incertains (pas de mcmod.info)")
		boolean  include_file;
		@CommandLine.Option(names = {"--prefix"},
				description = "Prefixe à appliquer aux urls lors de l'importation des fichiers")
		String   prefix = null;
		
		@CommandLine.Mixin
		ForgeMods.DossiersOptions dossiers;
		@CommandLine.Mixin
		ForgeMods.Help            help;
		
		public Integer call() throws MalformedURLException {
			DepotLocal depot = new DepotLocal(dossiers.depot);
			try {
				depot.importation();
			} catch (IOException | JSONException i) {
				System.err
						.println("Erreur de lecture des informations du dépot: " + i.getClass() + " " + i.getMessage());
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
			
			final URL prefix = include_file ? (this.prefix != null ? new URL(this.prefix)
					: depot.dossier.toUri().toURL()) : null;
			for (ModVersion version : importation) {
				final ModVersion reelle = depot.ajoutModVersion(
						new ModVersion(depot.ajoutMod(version.mod), version.version, version.mcversion));
				reelle.fusion(version);
				reelle.urls.removeIf(url -> url.getProtocol().equals("file"));
				
				if (include_file) {
					final Path nouveau_fichier = copieFichier(depot.dossier, version);
					if (nouveau_fichier != null)
						reelle.ajoutURL(new URL(prefix, depot.dossier.relativize(nouveau_fichier).toString()));
				}
			}
			System.out.println(String.format("%d versions importées.", importation.size()));
			System.out.println(String.format("%d", depot.sizeModVersion()));
			
			try {
				depot.sauvegarde();
			} catch (IOException i) {
				System.err.println(
						String.format("Impossible de sauvegarder le dépot local à '%s': %s %s", depot.dossier,
								getClass(), i.getMessage()));
				return 1;
			}
			return 0;
		}
		
		private Path copieFichier(Path dossier_depot, ModVersion version) {
			Optional<URL> fichier = version.urls.stream().filter(u -> u.getProtocol().equals("file")).findFirst();
			if (fichier.isPresent()) {
				try {
					final Path source = Path.of(fichier.get().toURI());
					final Path destination = Dossiers.dossierModDepot(dossier_depot, version.mod.modid)
							.resolve(source.getFileName());
					destination.getParent().toFile().mkdirs();
					Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
					return destination;
				} catch (IOException | URISyntaxException e) {
					System.err.println(String.format("Impossible de copier le fichier '%s'!", fichier.get()));
				}
			} else {
				System.err.println(
						String.format("Aucun fichier à copier pour le mod %s=%s", version.mod.modid, version.version));
			}
			return null;
		}
	}
	
	@CommandLine.Command(name = "update", description = "Met à jour les informations du dépot à partir d'un autre.")
	public int update(@CommandLine.Mixin ForgeMods.Help help,
			@CommandLine.Option(names = {"-d", "--depot"}, description = "Dépot local") Path adresseDepot,
			@CommandLine.Option(names = {"-f", "--from"}, arity = "0..n", description = "Dépot distant spécifique")
					List<String> urlDistant, @CommandLine.Option(names = {"-c", "--clear"}, defaultValue = "false",
			description = "Remplace totalement le dépot initial par les informations téléchargées.") boolean clear) {
		final DepotLocal depotLocal = new DepotLocal(adresseDepot);
		if (!clear) {
			try {
				depotLocal.importation();
			} catch (IOException | JSONException e) {
				System.err.println("Erreur de chargement du dépot local: " + e.getClass() + " " + e.getMessage());
				return 1;
			}
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
					try (InputStream s = url.openStream()) {
						final FichierTar tar = new FichierTar(s);
						depotLocal.synchronisationDepot(new DepotDistant() {
							@Override
							public InputStream fichierIndexDepot() throws FileNotFoundException {
								return tar.fichier("Mods.json").getInputStream();
							}
							
							@Override
							public InputStream fichierModDepot(String modid) throws FileNotFoundException {
								return tar.fichier(modid.substring(0, 1) + "/" + modid + "/" + modid + ".json")
										.getInputStream();
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
