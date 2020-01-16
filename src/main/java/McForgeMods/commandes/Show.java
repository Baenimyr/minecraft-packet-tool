package McForgeMods.commandes;

import McForgeMods.*;
import McForgeMods.depot.Depot;
import McForgeMods.depot.DepotInstallation;
import McForgeMods.depot.DepotLocal;
import McForgeMods.outils.Dossiers;
import picocli.CommandLine;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
		ForgeMods.Help help;
		@CommandLine.Mixin
		ShowOptions              show;
		@CommandLine.Mixin
		Dossiers.DossiersOptions dossiers;
		
		@CommandLine.Option(names = {"--absents"}, description = "Affiche les dépendances manquantes.")
		boolean missing = false;
		
		@Override
		public Integer call() {
			Gestionnaire gest = new Gestionnaire(dossiers.minecraft);
			
			Map<String, VersionIntervalle> liste;
			if (show.all) {
				liste = gest.listeDependances();
				System.out.println(String.format("%d dépendances", liste.size()));
			} else if (missing) {
				liste = gest.dependancesAbsentes();
				System.out.println(String.format("%d absents", liste.size()));
			} else {
				liste = gest.listeDependances();
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
		ForgeMods.Help help;
		@CommandLine.Mixin
		Dossiers.DossiersOptions dossiers;
		
		@CommandLine.Parameters(arity = "0..1", index = "0", description = "regex pour la recherche")
		String recherche = null;
		
		@CommandLine.Option(names = {"-a", "--all"}, defaultValue = "false",
				description = "Lecture des mods dans le dépot, pas seulement le dossier minecraft.")
		boolean all;
		
		@CommandLine.Option(names = {"--mcversion"},
				description = "Limite l'affichage aux mods compatibles avec une version de minecraft.")
		String mcversion;
		
		@Override
		public Integer call() {
			DepotLocal local;
			Depot dep;
			Pattern regex = recherche != null ? Pattern.compile(recherche) : null;
			
			try {
				local = new DepotLocal(dossiers.depot);
				local.importation();
			} catch (IOException erreur) {
				System.err.println("Erreur de lecture du dépot.");
				local = null;
			}
			
			if (all) {
				if (local == null) return 1;
				dep = local;
			} else {
				DepotInstallation depot = new DepotInstallation(dossiers.minecraft);
				depot.analyseDossier(local);
				dep = depot;
			}
			
			
			List<String> modids = new ArrayList<>(dep.getModids());
			modids.sort(String::compareTo);
			for (String modid : modids) {
				if (regex != null && !regex.matcher(modid).find()) continue;
				
				System.out.println(modid);
				for (ModVersion mv : dep.getModVersions(modid)) {
					if (mcversion == null || Version.read(mcversion).equals(mv.mcversion))
						System.out.println(String.format("\t+ %s [%s]", mv.version, mv.mcversion));
				}
			}
			
			return 0;
		}
	}
}
