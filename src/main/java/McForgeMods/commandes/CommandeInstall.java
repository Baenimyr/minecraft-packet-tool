package McForgeMods.commandes;

import McForgeMods.ForgeMods;
import McForgeMods.ModVersion;
import McForgeMods.Version;
import McForgeMods.VersionIntervalle;
import McForgeMods.depot.ArbreDependance;
import McForgeMods.depot.DepotInstallation;
import McForgeMods.depot.DepotLocal;
import McForgeMods.outils.TelechargementHttp;
import picocli.CommandLine;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * La commande <i>install</i> permet de récupérer les fichiers de mod présents sur le réseau. Pour l'installation d'un
 * nouveau mod, l'utilisateur peut spécifier la version de son choix. Elle sera interpretée comme un intervalle et la
 * version maximale possible sera installée. Si l'utilisateur spécifie une version de minecraft, spécifier la version du
 * mod devient facultatif et c'est la version maximale
 */
@CommandLine.Command(name = "install", sortOptions = false, resourceBundle = "mcforgemods/lang/Install")
public class CommandeInstall implements Callable<Integer> {
	static final int ERREUR_NOM     = 10;
	static final int ERREUR_MODID   = ERREUR_NOM + 1;
	static final int ERREUR_VERSION = ERREUR_NOM + 2;
	
	static final ExecutorService executor = new ThreadPoolExecutor(0, 4, 1L, TimeUnit.SECONDS,
			new LinkedBlockingQueue<>());
	
	@CommandLine.Option(names = {"-h", "--help"}, usageHelp = true)
	boolean help;
	
	@CommandLine.Parameters(arity = "1..n", descriptionKey = "mods")
	ArrayList<String> mods;
	
	@CommandLine.Mixin
	ForgeMods.DossiersOptions dossiers;
	
	@CommandLine.Option(names = {"-mc", "--mcversion"})
	String mcversion;
	
	@CommandLine.Option(names = {"--no-dependencies"}, negatable = true, descriptionKey = "dependencies")
	boolean dependances = true;
	
	/*@CommandLine.Option(names = {"-y", "--yes"}, defaultValue = "false",
			description = "Répond automatiquement oui à toutes les questions.")
	boolean yes;*/
	
	/*@CommandLine.Option(names = {"--only-update"},
			description = "Interdit l'installation de nouveaux mods. Les mods de la liste seront mis à jour.")
	boolean only_update;*/
	
	@CommandLine.Option(names = {"-s", "--simulate", "--dry-run"}, defaultValue = "false", descriptionKey = "simulate")
	boolean dry_run;
	
	@CommandLine.Option(names = {"-f", "--force"}, defaultValue = "false",
			description = "Termine l'installation pour" + " tous les mods possibles")
	boolean force;
	
	
	@Override
	public Integer call() {
		final DepotLocal depotLocal = new DepotLocal(dossiers.depot);
		final DepotInstallation depotInstallation = new DepotInstallation(dossiers.minecraft);
		/* Liste des mods à installer. */
		final ArbreDependance arbre_dependances = new ArbreDependance(depotLocal);
		
		try {
			depotLocal.importation();
			depotInstallation.analyseDossier(depotLocal);
		} catch (IOException i) {
			System.err.println("[ERROR] Erreur de lecture du dépot !");
			return 1;
		}
		
		if (this.mcversion != null) {
			if (depotInstallation.mcversion == null) depotInstallation.mcversion = VersionIntervalle.read(this.mcversion);
			else if (!depotInstallation.mcversion.equals(Version.read(this.mcversion))) {
				System.err.println("[ERROR] Il est impossible de changer la version de minecraft ici.");
				return 1;
			}
		} else if (depotInstallation.mcversion == null) {
			System.err.println(
					"Aucune version de minecraft spécifiée. Veuillez compléter l'option -mc une première fois.");
			return 1;
		};
		arbre_dependances.mcversion.intersection(depotInstallation.mcversion);
		
		/* Liste des installations explicitement demandées par l'utilisateur. */
		final Map<String, VersionIntervalle> demandes;
		try {
			demandes = VersionIntervalle.lectureDependances(this.mods);
		} catch (IllegalArgumentException iae) {
			System.err.println("[ERROR] " + iae.getMessage());
			return 1;
		}
		for (final Map.Entry<String, VersionIntervalle> demande : demandes.entrySet()) {
			if (!depotLocal.getModids().contains(demande.getKey())) {
				System.err.printf("[ERROR] Modid inconnu: '%s'%n", demande.getKey());
				return ERREUR_MODID;
			}
			// Vérification que l'intervalle n'est pas trop large.
			if (demande.getValue().minimum() == null && demande.getValue().maximum() == null && (
					depotInstallation.mcversion.minimum() == null || depotInstallation.mcversion.maximum() == null)) {
				System.err.printf("[ERROR] Vous devez spécifier une version, pour le mod '%s' ou " + "minecraft.%n",
						demande.getKey());
				return ERREUR_VERSION;
			}
			
			arbre_dependances.ajoutContrainte(demande.getKey(), demande.getValue());
		}
		
		// Ajout de toutes les installations manuelles dans l'installation
		for (String modid : depotInstallation.getModids()) {
			for (ModVersion mversion : depotInstallation.getModVersions(modid)) {
				if (depotInstallation.estManuel(mversion) && !arbre_dependances.listeModids()
						.contains(mversion.modid) && mversion.mcversion.englobe(depotInstallation.mcversion)) {
					// seulement les choix utilisateur les plus récents
					arbre_dependances.ajoutContrainte(mversion);
				}
			}
		}
		
		/* Association entre un modid et la version selectionnée pour l'installation */
		if (this.dependances) {
			arbre_dependances.resolution();
		}
		
		/* Résolution des versions. Cherche dans le dépot si il existe des mods qui correspondent aux versions
		demandées. Si le dépot d'installation contient déjà un mod qui satisfait la condition, aucun téléchargement
		n'est nécessaire.
		 */
		final List<ModVersion> installations = new ArrayList<>();
		for (final String modid : arbre_dependances.listeModids()) {
			final VersionIntervalle intervalle_requis = arbre_dependances.intervalle(modid);
			if (modid.equalsIgnoreCase("forge")) continue;
			
			if (depotInstallation.contains(modid) && depotInstallation.getModVersions(modid).stream()
					.anyMatch(mv -> intervalle_requis.correspond(mv.version))) continue;
			if (!depotLocal.contains(modid)) {
				System.err.printf("Modid requis inconnu: '%s'%n", modid);
				if (!this.force) return ERREUR_MODID;
			} else {
				Optional<ModVersion> candidat = depotLocal.getModVersions(modid).stream()
						.filter(mv -> intervalle_requis.correspond(mv.version))
						.max(Comparator.comparing(mv -> mv.version));
				if (candidat.isPresent()) {
					installations.add(candidat.get());
				} else {
					System.err.printf("Aucune version disponible pour le mod requis '%s@%s' et minecraft %s.%n", modid,
							arbre_dependances.intervalle(modid), mcversion);
					if (!this.force) return ERREUR_VERSION;
				}
			}
		}
		
		if (installations.size() != 0) {
			System.out.println("Installation des nouveaux mods:");
			StringJoiner joiner = new StringJoiner(" ");
			installations.forEach(mv -> joiner.add(mv.modid + "=" + mv.version));
			System.out.println("\t" + joiner.toString());
			
			if (!dry_run) {
				// Déclenche le téléchargement des mods
				Map<ModVersion, CompletableFuture<Void>> telechargements = new HashMap<>();
				for (ModVersion mversion : installations) {
					CompletableFuture<Void> t = CompletableFuture
							.supplyAsync(new InstallationMod(mversion, depotInstallation, depotLocal), executor).thenAcceptAsync(succes -> {
								if (succes) {
									synchronized (depotInstallation) {
										depotInstallation.installation(mversion, demandes.containsKey(mversion.modid));
										depotInstallation.suppressionConflits(mversion);
									}
								}
							});
					telechargements.put(mversion, t);
				}
				telechargements.forEach((mversion, cf) -> {
					try {
						cf.get();
					} catch (InterruptedException ignored) {
					} catch (ExecutionException erreur) {
						System.err.printf("Erreur téléchargement de '%s': %s%n", mversion, erreur.getMessage());
					}
				});
			}
		} else {
			System.out.println("Pas de nouveaux mods à télécharger.");
			return 0;
		}
		
		try {
			depotInstallation.close();
		} catch (IOException e) {
			System.err.println("Impossible de sauvegarder la configuration de l'installation.");
			return 1;
		}
		return 0;
	}
	
	private static class InstallationMod implements Supplier<Boolean> {
		final DepotLocal        local;
		final ModVersion        modVersion;
		final DepotInstallation minecraft;
		private Path cible = null;
		
		InstallationMod(ModVersion modVersion, DepotInstallation minecraft, DepotLocal local) {
			this.modVersion = modVersion;
			this.minecraft = minecraft;
			this.local = local;
		}
		
		/**
		 * Change le nom du fichier de destination. Le fichier jar peut avoir n'importe quel nom et le recupère
		 * généralement le meme que le fichier source.
		 *
		 * @param nom: nom du fichier, ne doit pas contenir de caractères comme '/'.
		 */
		private void renommerFichier(String nom) {
			this.cible = modVersion.dossierInstallation(minecraft.dossier).resolve(nom);
		}
		
		@Override
		public Boolean get() {
			List<URL> fichiers = modVersion.urls.stream().filter(url -> url.getProtocol().equals("file"))
					.collect(Collectors.toList());
			// Ajoute les fichiers éventuellement dans le cache
			for (String alias : modVersion.alias)
				try {
					fichiers.add(local.dossierCache(modVersion).resolve(alias).toUri().toURL());
				} catch (MalformedURLException ignored) {
				}
			List<URL> http = modVersion.urls.stream()
					.filter(url -> url.getProtocol().equals("http") || url.getProtocol().equals("https"))
					.collect(Collectors.toList());
			if (fichiers.size() == 0 && http.size() == 0) {
				System.err.printf("[Install] aucun lien de téléchargement pour %s.%n", modVersion);
				return false;
			}
			
			Path dossier = modVersion.dossierInstallation(minecraft.dossier);
			if (Files.notExists(dossier)) {
				try {
					Files.createDirectories(dossier);
				} catch (IOException e) {
					throw new RuntimeException("Impossible de créer le dossier de destination.");
				}
			}
			
			// Tentative de copie de fichier
			for (URL url : fichiers) {
				try {
					Path source = Path.of(url.toURI().getPath());
					if (!source.toFile().exists()) continue;
					this.renommerFichier(source.getFileName().toString());
					Files.copy(source, cible);
					synchronized (System.out) {
						System.out.printf("%-20s %-20s OK%n", modVersion.modid, modVersion.version);
					}
					return true;
				} catch (URISyntaxException e) {
					System.err.printf("Format d'url incorrect: '%s'%n", url.toString());
				} catch (IOException e) {
					System.err.printf("[Install] impossible de copier le fichier '%s'.%n", url.toString());
					System.err.println("\t" + e.getMessage());
				}
			}
			
			// Tentative de téléchargement HTTP
			for (URL url : http) {
				String nom = url.getPath();
				nom = nom.substring(nom.lastIndexOf('/') + 1);
				if (!nom.endsWith(".jar")) nom = modVersion.toStringStandard() + ".jar";
				
				try {
					TelechargementHttp telechargement = new TelechargementHttp(url.toURI(),
							modVersion.dossierInstallation(minecraft.dossier).resolve(nom).toFile());
					HttpResponse<String> connexion = telechargement.recupereInformations();
					
					if (connexion.statusCode() >= 400) {
						throw new IOException(
								String.format("Server sent code %d for '%s'", connexion.statusCode(), connexion.uri()));
					}
					
					List<String> content = connexion.headers().allValues("Content-Disposition");
					final Pattern filename = Pattern.compile("filename=\"(.+)\"");
					for (String info : content) {
						Matcher m = filename.matcher(info);
						if (m.find()) {
							this.renommerFichier(m.group(1));
							telechargement.fichier = this.cible.toFile();
							System.out.println(
									"[Telechargement] [DEBUG] nouveau nom : " + telechargement.fichier.getName());
							break;
						}
					}
					
					long taille = telechargement.telechargement();
					if (taille == 0) continue;
					synchronized (System.out) {
						System.out.printf("%-20s %-20s %.1f Ko%n", modVersion.modid, modVersion.version,
								(float) telechargement.telecharge / 1024);
					}
					return true;
				} catch (IOException | InterruptedException | URISyntaxException io) {
					System.err.printf("[Install] impossible de télécharger le fichier: %s%n", url);
					System.err.println("\t" + io.getMessage());
				}
			}
			
			System.err.printf("Téléchargement échoué de %s%n", modVersion);
			return false;
		}
	}
}
