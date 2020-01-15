package McForgeMods.commandes;

import McForgeMods.*;
import McForgeMods.depot.Depot;
import McForgeMods.depot.DepotInstallation;
import McForgeMods.depot.DepotLocal;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

@CommandLine.Command(name = "show", description = "Affichage d'informations",
		subcommands = {Show.showDependencies.class, Show.Mods.class})
public class Show implements Runnable {
	@CommandLine.Option(names = {"--help"}, usageHelp = true)
	boolean help;
	
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
		ShowOptions               show;
		@CommandLine.Mixin
		ForgeMods.DossiersOptions dossiers;
		
		@CommandLine.Option(names = {"--absents"}, description = "Affiche les dépendances manquantes.")
		boolean missing = false;
		
		@Override
		public Integer call() {
			Path minecraft = DepotInstallation.resolutionDossierMinecraft(dossiers.minecraft);
			if (minecraft == null) {
				System.out.println("Impossible de trouver un dossier d'installation minecraft.");
				return 1;
			}
			
			System.out.print("Analyse du dossier '" + minecraft + "'...\t");
			Gestionnaire gest = new Gestionnaire(minecraft);
			System.out.println(gest.installation.getModids().size() + " mods");
			
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
		ForgeMods.DossiersOptions dossiers;
		
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
				System.out.print("Analyse du dossier '" + local.dossier + "'...\t");
				System.out.println(local.getModids().size() + " mods");
			} catch (IOException erreur) {
				System.out.println("Erreur de lecture du dépot.");
				local = null;
			}
			
			if (all) {
				if (local == null) return 1;
				dep = local;
			} else {
				Path minecraft = DepotInstallation.resolutionDossierMinecraft(dossiers.minecraft);
				if (minecraft == null) {
					System.out.println("Impossible de trouver un dossier d'installation minecraft.");
					return 1;
				}
				DepotInstallation depot = new DepotInstallation(minecraft);
				depot.analyseDossier(local);
				System.out.print("Analyse du dossier '" + depot.dossier + "'...\t");
				System.out.println(depot.getModids().size() + " mods");
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
