package McForgeMods.commandes;

import McForgeMods.ModVersion;
import McForgeMods.VersionIntervalle;
import McForgeMods.depot.DepotInstallation;
import McForgeMods.depot.DepotLocal;
import McForgeMods.outils.Dossiers;
import McForgeMods.outils.Transfert;
import picocli.CommandLine;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/**
 * La commande <i>install</i> permet de récupérer les fichiers de mod présents sur le réseau. Pour l'installation d'un
 * nouveau mod, l'utilisateur peut spécifier la version de son choix. Elle sera interpretée comme un intervalle et la
 * version maximale possible sera installée. Si l'utilisateur spécifie une version de minecraft, spécifier la version du
 * mod devient facultatif et c'est la version maximale
 */
@CommandLine.Command(name = "install", sortOptions = false, description = {"Permet l'installation de mods.",
		"Chaque mod de la liste sera installé ou mis à jour vers la dernière version compatibles avec le reste des "
				+ "mods."}, exitCodeListHeading = "%nListe des codes d'erreur:%n",
		exitCodeList = {CommandeInstall.ERREUR_NOM + ":erreur de nom", CommandeInstall.ERREUR_MODID + ":modid inconnu",
				CommandeInstall.ERREUR_RESSOURCE + ":erreur de ressource",
				CommandeInstall.ERREUR_URL + ":aucun lien disponible"})
public class CommandeInstall implements Callable<Integer> {
	static final int ERREUR_NOM       = 10;
	static final int ERREUR_MODID     = ERREUR_NOM + 1;
	static final int ERREUR_VERSION   = ERREUR_NOM + 2;
	static final int ERREUR_RESSOURCE = 20;
	static final int ERREUR_URL       = ERREUR_RESSOURCE + 1;
	
	@CommandLine.Option(names = {"-h", "--help"}, usageHelp = true)
	boolean help;
	
	@CommandLine.Parameters(arity = "1..n", description = "Liste des mods à installer.")
	ArrayList<String> mods;
	
	@CommandLine.Mixin
	Dossiers.DossiersOptions dossiers;
	
	@CommandLine.Option(names = {"--mcversion"}, arity = "1",
			description = "Permet de choisir un version de minecraft. Recommandé.")
	String mcversion;
	
	@CommandLine.Option(names = {"--dependencies"}, negatable = true, defaultValue = "true",
			description = "Autorise ou empêche l'installation des dépendances si besoin.")
	boolean dependances;
	
	/*@CommandLine.Option(names = {"-y", "--yes"}, defaultValue = "false",
			description = "Répond automatiquement oui à toutes les questions.")
	boolean yes;*/
	
	@CommandLine.Option(names = {"--only-update"},
			description = "Interdit l'installation de nouveaux mods. Les mods de la liste seront mis à jour.")
	boolean only_update;
	
	@CommandLine.Option(names = {"-s", "--simulate", "--dry-run"}, defaultValue = "false",
			description = "Simule l'installation.")
	boolean dry_run;
	
	
	@Override
	public Integer call() {
		final DepotLocal depotLocal = new DepotLocal(dossiers.depot);
		final DepotInstallation depotInstallation = new DepotInstallation(dossiers.minecraft);
		/* Liste des mods à installer. */
		final List<ModVersion> installations = new ArrayList<>();
		
		final VersionIntervalle mcversion = this.mcversion != null ? VersionIntervalle.read(this.mcversion) : null;
		
		try {
			depotLocal.importation();
			depotInstallation.analyseDossier(depotLocal);
		} catch (IOException i) {
			System.err.println("Erreur de lecture du dépot !");
			return 1;
		}
		
		for (final Map.Entry<String, VersionIntervalle> demande : VersionIntervalle.lectureDependances(this.mods)
				.entrySet()) {
			if (!depotLocal.getModids().contains(demande.getKey())) {
				System.err.println(String.format("Modid inconnu: '%s'", demande.getKey()));
				return ERREUR_MODID;
			}
			// Vérification que l'intervalle n'est pas trop large.
			if (demande.getValue() == null && mcversion == null) {
				// TODO: remplacer par l'instance ouvert de VersionIntervalle
				System.err.println(String.format("Vous devez spécifier une version, pour le mod '%s' ou minecraft.",
						demande.getKey()));
				return ERREUR_VERSION;
			}
			
			Optional<ModVersion> candidat = depotLocal.getModVersions(demande.getKey()).stream()
					.filter(mv -> mcversion == null || mcversion.correspond(mv.mcversion))
					.filter(mv -> demande.getValue() == null || demande.getValue().correspond(mv.version))
					.max(Comparator.comparing(mv -> mv.version));
			if (candidat.isPresent()) {
				installations.add(candidat.get());
			} else {
				System.err.println(
						String.format("Aucune version disponible pour '%s@%s'.", demande.getKey(), demande.getValue()));
				return ERREUR_VERSION;
			}
		}
		
		if (this.dependances) {
			final Map<String, VersionIntervalle> dependances = depotLocal.listeDependances(installations);
			for (final Map.Entry<String, VersionIntervalle> dependance : dependances.entrySet()) {
				if (dependance.getKey().equalsIgnoreCase("forge")) continue;
				
				Optional<ModVersion> conflit = installations.stream()
						.filter(mv -> mv.mod.modid.equals(dependance.getKey()))
						.filter(mv -> !dependance.getValue().correspond(mv.version)).findAny();
				if (conflit.isPresent()) {
					System.err.println(String.format("Le mod requis '%s@%s' est en concurrence avec '%s@=%s'.",
							dependance.getKey(), dependance.getValue() == null ? "" : dependance.getValue(),
							conflit.get().mod.modid, conflit.get().version));
					return ERREUR_NOM;
				} else if (!depotLocal.contains(dependance.getKey())) {
					System.err.println(String.format("Modid requis inconnu: '%s'", dependance.getKey()));
					return ERREUR_MODID;
				} else {
					Optional<ModVersion> candidat = depotLocal.getModVersions(dependance.getKey()).stream()
							.filter(mv -> mcversion == null || mcversion.correspond(mv.mcversion))
							.filter(mv -> dependance.getValue() == null || dependance.getValue().correspond(mv.version))
							.max(Comparator.comparing(mv -> mv.version));
					if (candidat.isPresent()) {
						if (!this.only_update || !(!depotInstallation.contains(candidat.get().mod) || depotInstallation
								.getModVersions(candidat.get().mod).stream()
								.noneMatch(mv -> mv.mcversion.equals(candidat.get().mcversion))))
							// Ignore les nouveaux mods: mod absent ou aucune version de la même version de minecraft
							installations.add(candidat.get());
					} else {
						System.err.println(String.format("Aucune version disponible pour le mod requis '%s@%s'.",
								dependance.getKey(), dependance.getValue()));
						return ERREUR_VERSION;
					}
				}
			}
		}
		
		return telechargementMods(installations, depotInstallation);
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
	private int telechargementMods(Iterable<ModVersion> installations, final DepotInstallation depotInstallation) {
		System.out.println("Installation des mods:");
		StringJoiner joiner = new StringJoiner(", ");
		installations.forEach(mv -> joiner.add(mv.mod.modid + "=" + mv.version));
		System.out.println(joiner.toString());
		
		
		final ExecutorService executor = new ThreadPoolExecutor(0, 2, 1L, TimeUnit.SECONDS,
				new LinkedBlockingQueue<>());
		final List<TelechargementMod> telechargements = new ArrayList<>();
		for (ModVersion modVersion : installations) {
			if (depotInstallation.contains(modVersion)) {
				System.out.println(String.format("%-20s %-20s OK", modVersion.mod.modid, modVersion.version));
			} else {
				try {
					URL url_final = null;
					for (URL url : modVersion.urls) {
						if (url.getProtocol().equals("file") || url.getProtocol().equals("http") || url.getProtocol()
								.equals("https")) {
							url_final = url;
							break;
						}
					}
					if (url_final == null) System.err.println(
							String.format("Aucun lien de téléchargement pour '%s=%s'", modVersion.mod.modid,
									modVersion.version));
					else telechargements.add(new TelechargementMod(modVersion, depotInstallation, url_final));
				} catch (MalformedURLException u) {
					System.err.println(
							String.format("Erreur de lien pour '%s=%s'", modVersion.mod.modid, modVersion.version));
					return ERREUR_URL;
				}
			}
		}
		
		if (dry_run) return 0;
		
		final Map<TelechargementMod, Future<Boolean>> tasks = new LinkedHashMap<>();
		telechargements.forEach(t -> tasks.put(t, executor.submit(t)));
		for (Map.Entry<TelechargementMod, Future<Boolean>> task : tasks.entrySet()) {
			final TelechargementMod tele = task.getKey();
			final Future<Boolean> future = task.getValue();
			try {
				future.get();
			} catch (ExecutionException | InterruptedException erreur) {
				System.err.println(
						String.format("Erreur téléchargement de '%s': %s", tele.modVersion, erreur.getMessage()));
				erreur.printStackTrace(System.err);
			}
		}
		return 0;
	}
	
	private static class TelechargementMod implements Callable<Boolean> {
		final ModVersion        modVersion;
		final DepotInstallation minecraft;
		final URL               url;
		final Transfert         transfert;
		
		TelechargementMod(ModVersion modVersion, DepotInstallation minecraft, URL url) throws MalformedURLException {
			this.modVersion = modVersion;
			this.minecraft = minecraft;
			this.url = url;
			this.transfert = new Transfert(url, this.fichierCible());
		}
		
		@Override
		public Boolean call() throws Exception {
			final long resultat = this.transfert.call();
			if (resultat >= 0) {
				synchronized (this.minecraft) {
					if (this.minecraft.contains(this.modVersion.mod))
						this.minecraft.getModVersions(this.modVersion.mod).stream()
								.filter(v -> !v.equals(this.modVersion)).forEach(mv -> {
							try {
								Files.deleteIfExists(Path.of(mv.urls.get(0).toURI()));
							} catch (URISyntaxException | IOException e) {
								e.printStackTrace();
							}
						});
				}
				synchronized (System.out) {
					System.out.println(String.format("%-20s %-20s %.1f Ko", modVersion.mod.modid, modVersion.version,
							(float) transfert.getTransfered() / 1024));
				}
			} else {
				System.err.println(String.format("Téléchargement échoué de %s", modVersion));
				return false;
			}
			return true;
		}
		
		private URL fichierCible() throws MalformedURLException {
			String nom = String.format("%s-%s-%s.jar", modVersion.mod.modid, modVersion.mcversion, modVersion.version);
			if (this.url.getPath().endsWith(".jar")) {
				nom = this.url.getPath().substring(this.url.getPath().lastIndexOf('/') + 1);
			}
			
			return Dossiers.dossierInstallationMod(minecraft.dossier, modVersion).resolve(nom).toUri().toURL();
		}
	}
}
