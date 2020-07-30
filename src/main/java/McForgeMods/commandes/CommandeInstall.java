package McForgeMods.commandes;

import McForgeMods.ForgeMods;
import McForgeMods.ModVersion;
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
	static final int ERREUR_NOM       = 10;
	static final int ERREUR_MODID     = ERREUR_NOM + 1;
	static final int ERREUR_VERSION   = ERREUR_NOM + 2;
	static final int ERREUR_RESSOURCE = 20;
	static final int ERREUR_URL       = ERREUR_RESSOURCE + 1;
	
	static final ExecutorService executor = new ThreadPoolExecutor(0, 4, 1L, TimeUnit.SECONDS,
			new LinkedBlockingQueue<>());
	
	@CommandLine.Option(names = {"-h", "--help"}, usageHelp = true)
	boolean help;
	
	@CommandLine.Parameters(arity = "1..n", descriptionKey = "mods")
	ArrayList<String> mods;
	
	@CommandLine.Mixin
	ForgeMods.DossiersOptions dossiers;
	
	@CommandLine.Option(names = {"-mc", "--mcversion"}, required = true)
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
		final ArbreDependance arbre_dependances = new ArbreDependance();
		final List<String> installation_manuelle = new ArrayList<>();
		
		VersionIntervalle mcversion =
				this.mcversion != null ? VersionIntervalle.read(this.mcversion) : VersionIntervalle.ouvert();
		arbre_dependances.mcversion.intersection(mcversion);
		
		try {
			depotLocal.importation();
			depotInstallation.analyseDossier(depotLocal);
		} catch (IOException i) {
			System.err.println("[ERROR] Erreur de lecture du dépot !");
			return 1;
		}
		
		final Map<String, VersionIntervalle> demandes;
		try {
			demandes = VersionIntervalle.lectureDependances(this.mods);
		} catch (IllegalArgumentException iae) {
			System.err.println("[ERROR] " + iae.getMessage());
			return 1;
		}
		for (final Map.Entry<String, VersionIntervalle> demande : demandes.entrySet()) {
			if (!depotLocal.getModids().contains(demande.getKey())) {
				System.err.println(String.format("[ERROR] Modid inconnu: '%s'", demande.getKey()));
				return ERREUR_MODID;
			}
			// Vérification que l'intervalle n'est pas trop large.
			if (demande.getValue().minimum() == null && demande.getValue().maximum() == null && (
					mcversion.minimum() == null || mcversion.maximum() == null)) {
				System.err.println(
						String.format("[ERROR] Vous devez spécifier une version, pour le mod '%s' ou " + "minecraft.",
								demande.getKey()));
				return ERREUR_VERSION;
			}
			
			arbre_dependances.ajoutModIntervalle(demande.getKey(), demande.getValue());
			installation_manuelle.add(demande.getKey());
		}
		
		// Ajout de toutes les installations manuelles dans l'installation
		for (String modid : depotInstallation.getModids()) {
			for (ModVersion mversion : depotInstallation.getModVersions(modid)) {
				if (mversion.mcversion.englobe(mcversion)
						&& depotInstallation.statusMod(mversion) != DepotInstallation.StatusInstallation.AUTO
						&& !arbre_dependances.listeModids().contains(mversion.modid)) {
					// seulement les choix utilisateur les plus récents
					arbre_dependances.ajoutMod(mversion);
				}
			}
		}
		
		/* Association entre un modid et la version selectionnée pour l'installation */
		Map<String, ModVersion> resolution;
		if (this.dependances) {
			resolution = arbre_dependances.extension(depotLocal);
		} else {
			resolution = new HashMap<>();
			for (final String modid : installation_manuelle) {
				if (depotLocal.contains(modid)) {
					Optional<ModVersion> candidat = depotLocal.getModVersions(modid).stream()
							.filter(mv -> mv.mcversion.englobe(mcversion))
							.filter(mv -> arbre_dependances.intervalle(modid).correspond(mv.version))
							.max(Comparator.comparing(mv -> mv.version));
					candidat.ifPresent(modVersion -> resolution.put(modid, modVersion));
				}
			}
		}
		
		/* Résolution des versions. Cherche dans le dépot si il existe des mods qui correspondent aux versions
		demandées. Si le dépot d'installation contient déjà un mod qui satisfait la condition, aucun téléchargement
		n'est nécessaire.
		 */
		final Iterable<String> dependances = this.dependances ? arbre_dependances.listeModids() : installation_manuelle;
		final List<ModVersion> installations = new ArrayList<>();
		for (final String modid : dependances) {
			if (modid.equalsIgnoreCase("forge")) continue;
			
			if (!depotLocal.contains(modid)) {
				System.err.println(String.format("Modid requis inconnu: '%s'", modid));
				if (!this.force) return ERREUR_MODID;
			} else if (!depotInstallation.contains(modid) || depotInstallation.getModVersions(modid).stream().noneMatch(
					mv -> mv.mcversion.englobe(mcversion) && arbre_dependances.intervalle(modid)
							.correspond(mv.version))) {
				
				if (resolution.containsKey(modid)) {
					installations.add(resolution.get(modid));
				} else {
					System.err.println(
							String.format("Aucune version disponible pour le mod requis '%s@%s' et minecraft %s.",
									modid, arbre_dependances.intervalle(modid), mcversion));
					if (!this.force) return ERREUR_VERSION;
				}
			}
		}
		
		if (installations.size() != 0) {
			System.out.println("Installation des nouveaux mods:");
			StringJoiner joiner = new StringJoiner(" ");
			installations.forEach(mv -> joiner.add(mv.modid + "=" + mv.version));
			System.out.println("\t" + joiner.toString());
		} else {
			System.out.println("Pas de nouveaux mods à télécharger.");
			depotInstallation.close();
			return 0;
		}
		
		if (!dry_run) {
			for (ModVersion mversion : installations)
				depotInstallation.installation(mversion,
						installation_manuelle.contains(mversion.modid) ? DepotInstallation.StatusInstallation.MANUELLE
								: DepotInstallation.StatusInstallation.AUTO);
			// Déclenche le téléchargement des mods
			int retour = telechargementMods(installations, depotInstallation, depotLocal);
			depotInstallation.close();
			return retour;
		} else depotInstallation.close();
		return 0;
	}
	
	/**
	 * Téléchargement effectif de la liste des mods.
	 * <p>
	 * Tous les éléments de la liste sont traités indépendemments. Si un mod demandé est déjà présent dans
	 * l'installation, son téléchargement est ignoré.
	 *
	 * @param installations: liste des mods à télécharger
	 * @param depotInstallation: dépôt lié à l'installation
	 * @return 0 si tout s'est bien passé.
	 */
	private int telechargementMods(Collection<ModVersion> installations, final DepotInstallation depotInstallation,
			final DepotLocal depotLocal) {
		
		final List<InstallationMod> telechargements = installations.stream()
				.map(modVersion -> new InstallationMod(modVersion, depotInstallation, depotLocal))
				.collect(Collectors.toList());
		
		try {
			List<Future<Void>> futures = executor.invokeAll(telechargements);
			for (int i = 0; i < telechargements.size(); i++) {
				final InstallationMod tele = telechargements.get(i);
				final Future<Void> future = futures.get(i);
				try {
					future.get();
				} catch (ExecutionException | InterruptedException erreur) {
					// System.err.println(String.format("Erreur téléchargement de '%s': %s", tele.modVersion, erreur.getMessage()));
					// erreur.printStackTrace(System.err);
				}
			}
		} catch (InterruptedException ie) {
			return -1;
		}
		return 0;
	}
	
	/**
	 * Suite d'instructions pour installer un mod. Il faut le télécharger, vérifier l'intégrité du fichier puis
	 * enregistrer la nouvelle installation et enfin supprimer les versions obsolètes.
	 */
	private static class InstallationMod implements Callable<Void> {
		final DepotLocal        local;
		final ModVersion        modVersion;
		final DepotInstallation minecraft;
		Executor executor;
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
		
		/**
		 * Trouve un téléchargement et récupère le fichier. Tente tous les fichiers locaux possibles, principalement des
		 * fichiers en cache à partir d'alias connus, puis les téléchargements http. Une requete HEAD est d'abord
		 * envoyée afin de déterminer le nom et la taille du fichier, avant de télécharger le fichier.
		 *
		 * @return {@code true} en cas de succès.
		 */
		private boolean telechargement() throws IOException {
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
			Path dossier = modVersion.dossierInstallation(minecraft.dossier);
			if (Files.notExists(dossier)) Files.createDirectories(dossier);
			
			// Tentative de copie de fichier
			for (URL url : fichiers) {
				try {
					Path source = Path.of(url.toURI().getPath());
					if (!source.toFile().exists()) continue;
					this.renommerFichier(source.getFileName().toString());
					Files.copy(source, cible);
					synchronized (System.out) {
						System.out.println(String.format("%-20s %-20s OK", modVersion.modid, modVersion.version));
					}
					return true;
				} catch (URISyntaxException | IOException ignored) {
				}
			}
			
			// Tentative de téléchargement HTTP
			for (URL url : http) {
				String nom = url.getPath();
				nom = nom.substring(nom.lastIndexOf('/') + 1);
				if (!nom.endsWith(".jar")) nom = modVersion.toStringStandard() + ".jar";
				
				TelechargementHttp telechargement = new TelechargementHttp(url,
						modVersion.dossierInstallation(minecraft.dossier).resolve(nom).toFile());
				try {
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
						System.out.println(String.format("%-20s %-20s %.1f Ko", modVersion.modid, modVersion.version,
								(float) telechargement.telecharge / 1024));
					}
					return true;
				} catch (URISyntaxException | InterruptedException ignored) {
				} catch (IOException io) {
					System.err.println(String.format("Impossible de contacter '%s'", url));
				}
			}
			
			System.err.println(String.format("Téléchargement échoué de %s", modVersion));
			return false;
		}
		
		private void verification() {
			if (cible.toFile().exists()) this.validation();
		}
		
		private void validation() {
			synchronized (this.minecraft) {
				this.minecraft.installations.get(modVersion.modid).fichier = this.minecraft.dossier
						.relativize(this.cible).toString();
			}
			this.suppressionAncienne();
		}
		
		/**
		 * Supprime les anciennes versions.
		 */
		private void suppressionAncienne() {
			synchronized (this.minecraft) {
				this.minecraft.suppressionConflits(this.modVersion);
			}
		}
		
		@Override
		public Void call() throws Exception {
			if (this.telechargement()) this.verification();
			return null;
		}
	}
}
