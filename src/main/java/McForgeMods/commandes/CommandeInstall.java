package McForgeMods.commandes;

import McForgeMods.ModVersion;
import McForgeMods.VersionIntervalle;
import McForgeMods.depot.DepotInstallation;
import McForgeMods.depot.DepotLocal;
import McForgeMods.outils.Dossiers;
import picocli.CommandLine;

import java.util.*;
import java.util.concurrent.Callable;

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
	public Integer call() throws Exception {
		final DepotLocal depotLocal = new DepotLocal(dossiers.depot);
		final DepotInstallation depotInstallation = new DepotInstallation(dossiers.minecraft);
		/* Liste des mods à installer. */
		final List<ModVersion> installations = new ArrayList<>();
		
		final VersionIntervalle mcversion = VersionIntervalle.read(this.mcversion);
		
		depotLocal.importation();
		depotInstallation.analyseDossier(depotLocal);
		
		for (Map.Entry<String, VersionIntervalle> demande : DepotInstallation.lectureDependances(this.mods)
				.entrySet()) {
			if (!depotLocal.getModids().contains(demande.getKey())) {
				System.out.println(String.format("Modid inconnu: '%s'", demande.getKey()));
				return ERREUR_MODID;
			}
			Optional<ModVersion> candidat = depotLocal.getModVersions(demande.getKey()).stream()
					.filter(mv -> mcversion.correspond(mv.mcversion))
					.filter(mv -> demande.getValue().correspond(mv.version))
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
		
		if (dry_run)
			return 0;
		
		for (ModVersion modVersion : installations) {
			if (modVersion.urls.size() == 0) {
				System.err.println(String.format("Aucun lien de téléchargement pour '%s=%s'.", modVersion.mod.modid,
						modVersion.version));
				return ERREUR_URL;
			}
		}
		
		return 0;
	}
}
