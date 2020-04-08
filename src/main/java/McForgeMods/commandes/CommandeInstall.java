package McForgeMods.commandes;

import McForgeMods.ForgeMods;
import McForgeMods.ModVersion;
import McForgeMods.VersionIntervalle;
import McForgeMods.depot.ArbreDependance;
import McForgeMods.depot.DepotInstallation;
import McForgeMods.depot.DepotLocal;
import McForgeMods.outils.Dossiers;
import McForgeMods.outils.Transfert;
import picocli.CommandLine;

import java.io.IOException;
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
	
	static final ExecutorService executor = new ThreadPoolExecutor(0, 4, 1L, TimeUnit.SECONDS,
			new LinkedBlockingQueue<>());
	
	@CommandLine.Option(names = {"-h", "--help"}, usageHelp = true)
	boolean help;
	
	@CommandLine.Parameters(arity = "1..n", description = "Liste des mods à installer.")
	ArrayList<String> mods;
	
	@CommandLine.Mixin
	ForgeMods.DossiersOptions dossiers;
	
	@CommandLine.Option(names = {"--mcversion"}, description = "Permet de choisir un version de minecraft. Recommandé.")
	String mcversion;
	
	@CommandLine.Option(names = {"--dependencies"}, negatable = true, defaultValue = "true",
			description = "Autorise ou empêche l'installation des dépendances si besoin.")
	boolean dependances;
	
	/*@CommandLine.Option(names = {"-y", "--yes"}, defaultValue = "false",
			description = "Répond automatiquement oui à toutes les questions.")
	boolean yes;*/
	
	/*@CommandLine.Option(names = {"--only-update"},
			description = "Interdit l'installation de nouveaux mods. Les mods de la liste seront mis à jour.")
	boolean only_update;*/
	
	@CommandLine.Option(names = {"-s", "--simulate", "--dry-run"}, defaultValue = "false",
			description = "Simule l'installation.")
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
		
		VersionIntervalle mcversion = this.mcversion != null ? VersionIntervalle.read(this.mcversion)
				: VersionIntervalle.ouvert;
		
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
			if (demande.getValue() == VersionIntervalle.ouvert && mcversion == VersionIntervalle.ouvert) {
				System.err.println(String.format("Vous devez spécifier une version, pour le mod '%s' ou minecraft.",
						demande.getKey()));
				return ERREUR_VERSION;
			}
			
			// TODO: détecter la version de minecraft pour limiter la suite
			
			arbre_dependances.ajoutModIntervalle(demande.getKey(), demande.getValue());
			installation_manuelle.add(demande.getKey());
		}
		
		// Ajout de toutes les installations manuelles dans l'installation
		for (String modid : depotInstallation.getModids()) {
			for (ModVersion mversion : depotInstallation.getModVersions(modid)) {
				if (mcversion.correspond(mversion.mcversion)
						&& depotInstallation.statusMod(mversion) != DepotInstallation.StatusInstallation.AUTO
						&& !arbre_dependances.listeModids().contains(mversion.mod.modid)) {
					// seulement les choix utilisateur les plus récents
					arbre_dependances.ajoutMod(mversion);
				}
			}
		}
		
		if (this.dependances) {
			arbre_dependances.extension(depotLocal);
		}
		
		/* Résolution des versions. Cherche dans le dépot si il existe des mods qui correspondent aux versions
		demandées. Si le dépot d'installation contient déjà un mod qui satisfait la condition, aucun téléchargement
		n'est nécessaire.
		 */
		final Set<String> dependances = arbre_dependances.listeModids();
		final List<ModVersion> installations = new ArrayList<>();
		for (final String modid : dependances) {
			if (modid.equalsIgnoreCase("forge")) continue;
			
			if (!depotLocal.contains(modid)) {
				System.err.println(String.format("Modid requis inconnu: '%s'", modid));
				if (!this.force) return ERREUR_MODID;
			} else if (!depotInstallation.contains(modid) || depotInstallation.getModVersions(modid).stream().noneMatch(
					mv -> mcversion.correspond(mv.mcversion) && arbre_dependances.intervalle(modid)
							.correspond(mv.version))) {
				
				Optional<ModVersion> candidat = depotLocal.getModVersions(modid).stream()
						.filter(mv -> mcversion == VersionIntervalle.ouvert || mcversion.correspond(mv.mcversion))
						.filter(mv -> arbre_dependances.intervalle(modid).correspond(mv.version))
						.max(Comparator.comparing(mv -> mv.version));
				if (candidat.isPresent()) {
					installations.add(candidat.get());
				} else {
					System.err.println(String.format("Aucune version disponible pour le mod requis '%s@%s'.", modid,
							arbre_dependances.intervalle(modid)));
					if (!this.force) return ERREUR_VERSION;
				}
			}
		}
		
		if (installations.size() != 0) {
			System.out.println("Installation des nouveaux mods:");
			StringJoiner joiner = new StringJoiner(", ");
			installations.forEach(mv -> joiner.add(mv.mod.modid + "=" + mv.version));
			System.out.println(joiner.toString());
		} else {
			System.out.println("Pas de nouveaux mods à télécharger.");
			return 0;
		}
		
		if (!dry_run) {
			for (ModVersion mversion : installations)
				depotInstallation.installation(mversion, installation_manuelle.contains(mversion.mod.modid)
						? DepotInstallation.StatusInstallation.MANUELLE : DepotInstallation.StatusInstallation.AUTO);
			depotInstallation.statusSauvegarde();
			// Déclenche le téléchargement des mods
			return telechargementMods(installations, depotInstallation);
		}
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
	private int telechargementMods(Collection<ModVersion> installations, final DepotInstallation depotInstallation) {
		
		final List<TelechargementMod> telechargements = new ArrayList<>();
		for (ModVersion modVersion : installations) {
			if (modVersion.urls.stream().noneMatch(
					url -> url.getProtocol().equals("file") || url.getProtocol().equals("http") || url.getProtocol()
							.equals("https"))) {
				System.err.println(String.format("Aucun lien de téléchargement pour '%s=%s'", modVersion.mod.modid,
						modVersion.version));
			} else {
				telechargements.add(new TelechargementMod(modVersion, depotInstallation));
			}
		}
		
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
				// erreur.printStackTrace(System.err);
			}
		}
		return 0;
	}
	
	private static class TelechargementMod implements Callable<Boolean> {
		final ModVersion        modVersion;
		final DepotInstallation minecraft;
		
		TelechargementMod(ModVersion modVersion, DepotInstallation minecraft) {
			this.modVersion = modVersion;
			this.minecraft = minecraft;
		}
		
		@Override
		public Boolean call() throws Exception {
			for (URL url : modVersion.urls) {
				String nom = String
						.format("%s-%s-%s.jar", modVersion.mod.modid, modVersion.mcversion, modVersion.version);
				if (url.getPath().endsWith(".jar")) {
					nom = url.getPath().substring(url.getPath().lastIndexOf('/') + 1);
				}
				final Transfert transfert = new Transfert(url,
						Dossiers.dossierInstallationMod(minecraft.dossier, modVersion).resolve(nom).toUri().toURL());
				try {
					final long resultat = transfert.call();
					if (resultat >= 0) {
						synchronized (this.minecraft) {
							if (this.minecraft.contains(this.modVersion.mod))
								this.minecraft.getModVersions(this.modVersion.mod).stream()
										.filter(v -> !v.equals(this.modVersion)).forEach(mv -> {
									try {
										Files.deleteIfExists(Path.of(mv.urls.get(0).toURI()));
									} catch (URISyntaxException | IOException e) {
										System.err.println(e.getClass() + ": " + e.getMessage());
									}
								});
						}
						synchronized (System.out) {
							System.out.println(
									String.format("%-20s %-20s %.1f Ko", modVersion.mod.modid, modVersion.version,
											(float) transfert.getTransfered() / 1024));
						}
						return true;
					}
				} catch (IOException e) {
					System.err.println(e.getMessage());
				}
			}
			
			System.err.println(String.format("Téléchargement échoué de %s", modVersion));
			return false;
		}
	}
}
