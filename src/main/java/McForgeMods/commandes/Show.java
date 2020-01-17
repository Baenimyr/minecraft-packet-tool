package McForgeMods.commandes;

import McForgeMods.ForgeMods;
import McForgeMods.Gestionnaire;
import McForgeMods.ModVersion;
import McForgeMods.VersionIntervalle;
import McForgeMods.depot.Depot;
import McForgeMods.depot.DepotInstallation;
import McForgeMods.depot.DepotLocal;
import McForgeMods.outils.Dossiers;
import picocli.CommandLine;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

@CommandLine.Command(name = "show", description = "Affichage d'informations",
		subcommands = {Show.showDependencies.class, Show.Mods.class})
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
				Map<String, VersionIntervalle> recherche = DepotInstallation.lectureDependances(mods);
				for (Map.Entry<String, VersionIntervalle> entry : recherche.entrySet()) {
					String modid = entry.getKey();
					VersionIntervalle version = entry.getValue();
					if (gest.depot.getModids().contains(modid)) {
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
	
	@CommandLine.Command(name = "mods", description = "Affiche les mods présents.")
	static class Mods implements Callable<Integer> {
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
}
