package McForgeMods.commandes;

import McForgeMods.ForgeMods;
import McForgeMods.Mod;
import McForgeMods.ModVersion;
import McForgeMods.VersionIntervalle;
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

@CommandLine.Command(name = "show", description = {"Affichage d'informations."},
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
	
	@CommandLine.Command(name = "dependencies", description = {"Permet de résoudre les dépendences connues. Si une "
			+ "dépendance n'est pas déclarée, elle ne sera pas détectée."})
	static class showDependencies implements Callable<Integer> {
		@CommandLine.Mixin
		ForgeMods.Help           help;
		@CommandLine.Mixin
		ShowOptions              show;
		@CommandLine.Mixin
		Dossiers.DossiersOptions dossiers;
		
		@CommandLine.Parameters(index = "0", arity = "0..n",
				description = "Limite l'affichage aux dépendances de certains mods (modid[@version])")
		ArrayList<String> mods;
		
		@CommandLine.Option(names = {"--missing"}, description = "Affiche les dépendances manquantes. Peut afficher "
				+ "des mods comme absents parce que non détectés dans le dossier d'installation.")
		boolean missing = false;
		
		@Override
		public Integer call() {
			final DepotLocal depotLocal = new DepotLocal(dossiers.depot);
			final DepotInstallation depotInstallation = new DepotInstallation(dossiers.minecraft);
			
			try {
				depotLocal.importation();
			} catch (IOException e) {
				System.err.println("Erreur de lecture du dépôt.");
				return 1;
			}
			depotInstallation.analyseDossier(depotLocal);
			
			// Liste des versions pour lesquels chercher les dépendances.
			List<ModVersion> listeRecherche;
			
			if (show.all) {
				listeRecherche = depotInstallation.getModids().stream()
						.flatMap(modid -> depotInstallation.getModVersions(modid).stream())
						.collect(Collectors.toList());
			} else if (mods != null && mods.size() > 0) {
				final List<ModVersion> resultat = new ArrayList<>();
				Map<String, VersionIntervalle> recherche = VersionIntervalle.lectureDependances(mods);
				for (Map.Entry<String, VersionIntervalle> entry : recherche.entrySet()) {
					String modid = entry.getKey();
					VersionIntervalle version = entry.getValue();
					if (depotLocal.contains(modid)) {
						Optional<ModVersion> trouvee = depotLocal.getModVersions(modid).stream()
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
				listeRecherche = resultat;
			} else {
				System.err.println("Nécessite une liste de travail.");
				return 4;
			}
			
			// Liste complète des dépendances nécessaire pour la liste des mods présent.
			final Map<String, VersionIntervalle> dependances = depotLocal.listeDependances(listeRecherche);
			Map<String, VersionIntervalle> liste;
			if (missing) {
				liste = depotInstallation.dependancesAbsentes(dependances);
				System.out.println(String.format("%d absents", liste.size()));
			} else {
				liste = dependances;
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
			DepotLocal local = new DepotLocal(dossiers.depot);
			Depot dep;
			Pattern regex = recherche != null ? Pattern.compile(recherche) : null;
			VersionIntervalle filtre_mc = mcversion != null ? VersionIntervalle.read(mcversion) : null;
			
			try {
				local.importation();
			} catch (IOException erreur) {
				System.err.println("Erreur de lecture du dépot.");
			}
			
			if (all) {
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
		public Integer call() {
			final DepotLocal depotLocal = new DepotLocal(depot);
			try {
				depotLocal.importation();
			} catch (IOException e) {
				System.err.println(String.format("Erreur de lecture du dépot: %s %s", e.getClass(), e.getMessage()));
			}
			final List<Mod> mods = new ArrayList<>();
			final List<ModVersion> versions = new ArrayList<>();
			
			Map<String, VersionIntervalle> demandes = VersionIntervalle.lectureDependances(recherche);
			for (Map.Entry<String, VersionIntervalle> rech : demandes.entrySet()) {
				Mod mod = depotLocal.getMod(rech.getKey());
				if (mod == null) System.err.println(String.format("Mod inconnu: '%s'", rech.getKey()));
				else if (rech.getValue() == null) {
					mods.add(mod);
				} else {
					List<ModVersion> modVersion = depotLocal.getModVersions(mod).stream()
							.filter(v -> rech.getValue().correspond(v.version)).collect(Collectors.toList());
					if (modVersion.size() > 0) versions.addAll(modVersion);
					else System.err.println(
							String.format("Aucune version disponible pour '%s@%s'", rech.getKey(), rech.getValue()));
				}
			}
			
			for (Mod mod : mods) {
				System.out.println(
						String.format("%s (%s):%n%s%n{url='%s', updateJSON='%s'}", mod.name, mod.modid, mod.description,
								mod.url, mod.updateJSON));
				StringJoiner joiner = new StringJoiner(" ");
				depotLocal.getModVersions(mod).forEach(mv -> joiner.add(mv.version.toString()));
				System.out.println("versions: " + joiner.toString());
				System.out.println();
			}
			
			for (ModVersion version : versions) {
				System.out.println(String.format("%s %s [%s]:", version.mod.modid, version.version, version.mcversion));
				StringJoiner joiner = new StringJoiner(",");
				version.requiredMods.entrySet().stream().sorted(Map.Entry.comparingByKey())
						.forEach(e -> joiner.add(e.getKey() + "@" + e.getValue()));
				System.out.println("requiredMods " + joiner.toString());
				System.out.println("urls\t" + Arrays.toString(version.urls.toArray()));
				System.out.println("alias\t" + Arrays.toString(version.alias.toArray()));
				System.out.println();
			}
			
			return 0;
		}
	}
}
