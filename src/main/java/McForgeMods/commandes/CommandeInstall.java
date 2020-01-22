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
import java.net.URL;
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
		"Chaque mod de la liste sera installé ou mis à jour vers la "
				+ "dernière version compatibles avec le reste des mods."},
		exitCodeListHeading = "%nListe des codes d'erreur:%n", exitCodeList = {
		CommandeInstall.ERREUR_NOM + ":erreur de nom",
		CommandeInstall.ERREUR_MODID + ":modid inconnu",
		CommandeInstall.ERREUR_RESSOURCE + ":erreur de ressource",
		CommandeInstall.ERREUR_URL + ":aucun lien disponible"})
public class CommandeInstall implements Callable<Integer> {
	static final int ERREUR_NOM       = 10;
	static final int ERREUR_MODID     = ERREUR_NOM + 1;
	static final int ERREUR_RESSOURCE = 20;
	static final int ERREUR_URL       = ERREUR_RESSOURCE + 1;
	
	@CommandLine.Option(names = {"-h", "--help"}, usageHelp = true)
	boolean help;
	
	@CommandLine.Parameters(arity = "1..n", description = "Liste des mods à installer.")
	ArrayList<String> mods;
	
	@CommandLine.Mixin
	Dossiers.DossiersOptions dossiers;
	
	@CommandLine.Option(names = {"--mcversion"}, required = true, arity = "1",
			description = "Permet de choisir un version de minecraft.")
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
		
		final VersionIntervalle mcversion = VersionIntervalle.read(this.mcversion);
		if (mcversion == null) {
			System.err.println("Vous devez spécifier une version de minecraft.");
			return 1;
		}
		
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
				System.out.println(String.format("Modid inconnu: '%s'", demande.getKey()));
				return ERREUR_MODID;
			}
			Optional<ModVersion> candidat = depotLocal.getModVersions(demande.getKey()).stream()
					.filter(mv -> mcversion.correspond(mv.mcversion))
					.filter(mv -> demande.getValue() == null || demande.getValue().correspond(mv.version))
					.max(Comparator.comparing(mv -> mv.version));
			if (candidat.isPresent()) {
				installations.add(candidat.get());
			} else {
				System.out.println(
						String.format("Aucune version disponible pour '%s@%s'.", demande.getKey(), demande.getValue()));
				return ERREUR_RESSOURCE;
			}
		}
		
		if (this.dependances) {
			final Map<String, VersionIntervalle> dependances = depotLocal.listeDependances(installations);
			for (Map.Entry<String, VersionIntervalle> dependance : dependances.entrySet()) {
				if (dependance.getKey().equalsIgnoreCase("forge")) continue;
				
				Optional<ModVersion> conflit = installations.stream()
						.filter(mv -> mv.mod.modid.equals(dependance.getKey())).findAny();
				if (conflit.isPresent()) {
					if (!dependance.getValue().correspond(conflit.get().version)) {
						System.err.println(String.format("Le mod requis '%s' est en concurrence avec '%s@%s'.",
								dependance.getKey(), conflit.get().mod.modid, conflit.get().version));
						return ERREUR_NOM;
					}
				} else if (!depotLocal.getModids().contains(dependance.getKey())) {
					System.err.println(String.format("Modid requis inconnu: '%s'", dependance.getKey()));
					return ERREUR_MODID;
				} else {
					Optional<ModVersion> candidat = depotLocal.getModVersions(dependance.getKey()).stream()
							.filter(mv -> mcversion.correspond(mv.mcversion))
							.filter(mv -> dependance.getValue().correspond(mv.version))
							.max(Comparator.comparing(mv -> mv.version));
					if (candidat.isPresent()) {
						if (this.only_update && depotInstallation.contains(candidat.get().mod))
							// Ignore les nouveaux mods
							continue;
						if (!depotInstallation.contains(candidat.get())) installations.add(candidat.get());
					} else {
						System.out.println(String.format("Aucune version disponible pour le mod requis '%s@%s'.",
								dependance.getKey(), dependance.getValue()));
						return ERREUR_RESSOURCE;
					}
				}
			}
		}
		
		System.out.println("Installation des mods:");
		StringJoiner joiner = new StringJoiner(", ");
		installations.forEach(mv -> joiner.add(mv.mod.modid + "=" + mv.version));
		System.out.println(joiner.toString());
		
		if (dry_run) return 0;
		
		final ExecutorService executor = Executors.newSingleThreadExecutor();
		final List<TelechargementMod> telechargements = new ArrayList<>();
		for (ModVersion modVersion : installations) {
			try {
				telechargements.add(new TelechargementMod(modVersion, depotInstallation.dossier));
			} catch (MalformedURLException u) {
				System.err.println(String.format("Aucun lien de téléchargement pour '%s=%s'", modVersion.mod.modid,
						modVersion.version));
				return ERREUR_URL;
			}
		}
		
		telechargements.forEach(t -> executor.submit(t.task));
		for (TelechargementMod tele : telechargements) {
			try {
				Long resultat = tele.task.get();
				if (resultat >= 0) System.out.println(
						String.format("%-40s %.1f Ko", tele.modVersion.mod.modid + "=" + tele.modVersion.version,
								(float) tele.transfert.getTransfered() / 1024));
				else {
					System.err.println(String.format("Erreur téléchargement de %s", tele.modVersion));
				}
			} catch (ExecutionException | InterruptedException erreur) {
				System.err.println(String.format("Erreur téléchargement de '%s': %s", tele.modVersion, erreur.getMessage()));
				// erreur.printStackTrace(System.err);
			}
		}
		
		return 0;
	}
	
	private static class TelechargementMod {
		final ModVersion       modVersion;
		/**
		 * Dossier .minecraft
		 */
		final Path             minecraft;
		final Transfert        transfert;
		final FutureTask<Long> task;
		
		TelechargementMod(ModVersion modVersion, Path minecraft) throws MalformedURLException {
			this.modVersion = modVersion;
			this.minecraft = minecraft;
			
			Transfert transfert = null;
			for (URL url : this.modVersion.urls) {
				if (url.getProtocol().equals("file") || url.getProtocol().equals("http") || url.getProtocol()
						.equals("https")) {
					transfert = new Transfert(url, this.fichierCible());
				}
			}
			if (transfert == null) throw new IllegalArgumentException("Aucun url valide");
			
			this.transfert = transfert;
			this.task = new FutureTask<>(this.transfert);
		}
		
		private URL fichierCible() throws MalformedURLException {
			return Dossiers.dossierInstallationMod(minecraft, modVersion).resolve(
					String.format("%s-%s-[%s].jar", modVersion.mod.modid, modVersion.version, modVersion.mcversion))
					.toUri().toURL();
		}
	}
}
