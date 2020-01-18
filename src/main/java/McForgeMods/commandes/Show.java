package McForgeMods.commandes;

import McForgeMods.*;
import McForgeMods.depot.Depot;
import McForgeMods.depot.DepotInstallation;
import McForgeMods.depot.DepotLocal;
import McForgeMods.outils.Dossiers;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@CommandLine.Command(name = "show", description = "Affichage d'informations",
		subcommands = {Show.showDependencies.class, Show.list.class, Show.description.class})
public class Show implements Runnable {
	@CommandLine.Mixin
	ForgeMods.Help help;
	
	@CommandLine.Spec
	CommandLine.Model.CommandSpec spec;
	
	/**
	 * Options communes à toutes les commandes
	 */
	static class ShowOptions {
		@CommandLine.Option(names = {"-a", "--all"}, description = "Affiche tous les mods et leurs dépendances")
		boolean all = false;
	}
	
	@Override
	public void run() {
		throw new CommandLine.ParameterException(spec.commandLine(), "Missing required subcommand");
	}
	
	@CommandLine.Command(name = "dependencies")
	static class showDependencies implements Callable<Integer> {
		@CommandLine.Mixin
		ForgeMods.Help           help;
		@CommandLine.Mixin
		ShowOptions              show;
		@CommandLine.Mixin
		Dossiers.DossiersOptions dossiers;
		
		@CommandLine.Parameters(index = "0", arity = "0..n",
				description = "Dépendances pour un mods présent spécifique.")
		ArrayList<String> mods;
		
		@CommandLine.Option(names = {"--missing"}, description = "Affiche les dépendances manquantes.")
		boolean missing = false;
		
		@Override
		public Integer call() {
			Gestionnaire gest = new Gestionnaire(dossiers.minecraft);
			List<ModVersion> filtre = null;
			
			if (mods != null && mods.size() > 0) {
				final List<ModVersion> resultat = new ArrayList<>();
				Map<String, VersionIntervalle> recherche = VersionIntervalle.lectureDependances(mods);
				for (Map.Entry<String, VersionIntervalle> entry : recherche.entrySet()) {
					String modid = entry.getKey();
					VersionIntervalle version = entry.getValue();
					if (gest.depot.contains(modid)) {
						Optional<ModVersion> trouvee = gest.depot.getModVersions(modid).stream()
								.filter(modVersion -> version.correspond(modVersion.version))
								.max(Comparator.comparing(mv -> mv.version));
						if (trouvee.isPresent()) resultat.add(trouvee.get());
						else {
							System.err.println(String.format("Version inconnue pour '%s': '%s'", modid, version));
							return 3;
						}
					} else {
						System.err.println(String.format("Modid inconnu: '%s'", modid));
						return 3;
					}
				}
				filtre = resultat;
			}
			
			Map<String, VersionIntervalle> liste;
			if (show.all) {
				liste = filtre == null ? gest.listeDependances() : gest.listeDependances(filtre);
				System.out.println(String.format("%d dépendances", liste.size()));
			} else if (missing) {
				liste = gest.dependancesAbsentes();
				System.out.println(String.format("%d absents", liste.size()));
			} else {
				liste = filtre == null ? gest.listeDependances() : gest.listeDependances(filtre);
				System.out.println(String.format("%d dépendances", liste.size()));
			}
			
			ArrayList<String> modids = new ArrayList<>(liste.keySet());
			modids.sort(String::compareTo);
			for (String dep : modids) {
				System.out.println(dep + " " + liste.get(dep));
			}
			return 0;
		}
	}
	
	@CommandLine.Command(name = "list", description = "Affiche les mods présents.")
	static class list implements Callable<Integer> {
		@CommandLine.Mixin
		ForgeMods.Help           help;
		@CommandLine.Mixin
		Dossiers.DossiersOptions dossiers;
		
		@CommandLine.Parameters(arity = "0..1", index = "0", description = "regex pour la recherche")
		String recherche = null;
		
		@CommandLine.Option(names = {"-a", "--all"}, defaultValue = "false",
				description = "Lecture des mods dans le dépot, pas seulement le dossier minecraft.")
		boolean all;
		
		@CommandLine.Option(names = {"--mcversion"},
				description = "Limite l'affichage aux mods compatibles avec une version de minecraft. Peut être une "
						+ "intervalle de version.")
		String mcversion;
		
		@Override
		public Integer call() {
			DepotLocal local;
			Depot dep;
			Pattern regex = recherche != null ? Pattern.compile(recherche) : null;
			VersionIntervalle filtre_mc = mcversion != null ? VersionIntervalle.read(mcversion) : null;
			
			try {
				local = new DepotLocal(dossiers.depot);
				local.importation();
			} catch (IOException erreur) {
				System.err.println("Erreur de lecture du dépot.");
				local = null;
			}
			
			if (all) {
				if (local == null) return 1;
				System.out.println(
						String.format("Dépot '%s': %d mods", local.dossier.toString(), local.sizeModVersion()));
				dep = local;
			} else {
				DepotInstallation depot = new DepotInstallation(dossiers.minecraft);
				depot.analyseDossier(local);
				System.out.println(String.format("Dossier '%s': %d mods", depot.dossier, depot.sizeModVersion()));
				dep = depot;
			}
			
			List<String> modids = new ArrayList<>(dep.getModids());
			modids.sort(String::compareTo);
			for (String modid : modids) {
				if (regex != null && !regex.matcher(modid).find()) continue;
				
				for (ModVersion mv : dep.getModVersions(modid)) {
					if (filtre_mc == null || filtre_mc.correspond(mv.mcversion))
						System.out.println(String.format("%-20s %-10s %s", modid, mv.version, mv.mcversion));
				}
			}
			
			return 0;
		}
	}
	
	@CommandLine.Command(name = "mod", description = "Affiche les informations d'une liste de mods.")
	static class description implements Callable<Integer> {
		@CommandLine.Mixin
		ForgeMods.Help help;
		
		@CommandLine.Option(names = {"-d", "--depot"}, description = "Dépot local à utiliser")
		public Path depot = null;
		
		@CommandLine.Parameters(arity = "1..n", index = "0", paramLabel = "mod[@version]",
				description = "liste de mods à afficher.")
		ArrayList<String> recherche = null;
		
		@Override
		public Integer call() throws Exception {
			final DepotLocal depotLocal = new DepotLocal(depot);
			depotLocal.importation();
			final List<Mod> mods = new ArrayList<>();
			final List<ModVersion> versions = new ArrayList<>();
			
			Map<String, VersionIntervalle> demandes = VersionIntervalle.lectureDependances(recherche);
			final VersionIntervalle versionvide = new VersionIntervalle();
			for (Map.Entry<String, VersionIntervalle> rech : demandes.entrySet()) {
				Mod mod = depotLocal.getMod(rech.getKey());
				if (mod == null) System.err.println(String.format("Mod inconnu: '%s'", rech.getKey()));
				else if (rech.getValue().equals(versionvide)) {
					mods.add(mod);
				} else {
					List<ModVersion> modVersion = depotLocal.getModVersions(mod).stream()
							.filter(v -> rech.getValue().correspond(v.version))
							.collect(Collectors.toList());
					if (modVersion.size() > 0) versions.addAll(modVersion);
					else System.err.println(
							String.format("Aucune version disponible pour '%s@%s'", rech.getKey(), rech.getValue()));
				}
			}
			
			for (Mod mod : mods) {
				System.out.println(
						String.format("%s (%s): \"%s\" {url='%s', updateJSON='%s'}", mod.name, mod.modid, mod.description,
								mod.url, mod.updateJSON));
			}
			
			for (ModVersion version : versions) {
				System.out.println(String.format("%s %s [%s]:", version.mod.modid, version.version,
						version.mcversion));
				System.out.print("requiredMods");
				version.requiredMods.entrySet().stream().sorted(Map.Entry.comparingByKey())
						.forEach(e -> System.out.print(" " + e.getKey() + "@" + e.getValue()));
				System.out.println();
				System.out.println("urls\t" + Arrays.toString(version.urls.toArray()));
				System.out.println("alias\t" + Arrays.toString(version.alias.toArray()));
				System.out.println();
			}
			
			return 0;
		}
	}
}
