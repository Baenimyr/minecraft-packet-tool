package McForgeMods.commandes;

import McForgeMods.ForgeMods;
import McForgeMods.Gestionnaire;
import McForgeMods.ModVersion;
import McForgeMods.VersionIntervalle;
import McForgeMods.depot.Depot;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

@CommandLine.Command(name = "show", description = "Affichage d'informations", subcommands = {Show.showDependencies.class, Show.Mods.class})
public class Show implements Runnable {
    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    /**
     * Options communes à toutes les commandes
     */
    static class ShowOptions {
        @CommandLine.Option(names = {"-h", "--help"}, usageHelp = true, description = "display this help message")
        boolean usageHelpRequested;

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
        ShowOptions show;
        @CommandLine.Mixin
        ForgeMods.DossiersOptions dossiers;

        @CommandLine.Option(names = {"--absents"}, description = "Affiche les dépendances manquantes.")
        boolean missing = false;

        @Override
        public Integer call() {
            Path minecraft = Gestionnaire.resolutionDossierMinecraft(dossiers.minecraft);
            if (minecraft == null) {
                System.out.println("Impossible de trouver un dossier d'installation minecraft.");
                return 1;
            }

            System.out.print("Analyse du dossier '" + minecraft + "'...\t");
            Gestionnaire gest = new Gestionnaire(minecraft);
            System.out.println(gest.installation.getModids().size() + " mods");

            if (show.all) {
                Map<String, VersionIntervalle> liste = gest.listeDependances();
                System.out.println(String.format("%d dépendances", liste.size()));
                for (Map.Entry<String, VersionIntervalle> dep : liste.entrySet()) {
                    System.out.println(dep.getKey() + " " + dep.getValue());
                }
            } else if (missing) {
                Map<String, VersionIntervalle> liste = gest.dependancesAbsentes();
                System.out.println(String.format("%d absents", liste.size()));
                for (Map.Entry<String, VersionIntervalle> dep : liste.entrySet()) {
                    System.out.println(dep.getKey() + " " + dep.getValue());
                }
            } else {
                Map<String, VersionIntervalle> liste = gest.listeDependances();
                System.out.println(String.format("%d dépendances", liste.size()));
                for (Map.Entry<String, VersionIntervalle> dep : liste.entrySet()) {
                    System.out.println(dep.getKey() + " " + dep.getValue());
                }
            }
            return 0;
        }
    }

    @CommandLine.Command(name = "mods", description = "affiche les mods présents")
    static class Mods implements Callable<Integer> {
        @CommandLine.Mixin
        ShowOptions show;
        @CommandLine.Mixin
        ForgeMods.DossiersOptions dossiers;

        @CommandLine.Parameters(arity = "0..1", index = "0", description = "regex pour la recherche")
        String recherche = null;

        @CommandLine.Option(names = {"-i", "--installes"}, defaultValue = "false", description = "restriction aux mods dans le repertoire minecraft")
        boolean installes;

        @CommandLine.Option(names = {"--mcversion"}, description = "Limite l'affichage aux mods compatibles avec une version de minecraft.")
        String mcversion;

        @Override
        public Integer call() {
            Path minecraft = Gestionnaire.resolutionDossierMinecraft(dossiers.minecraft);
            if (minecraft == null) {
                System.out.println("Impossible de trouver un dossier d'installation minecraft.");
                return 1;
            }

            System.out.print("Analyse du dossier '" + minecraft + "'...\t");
            Gestionnaire gest = new Gestionnaire(minecraft);
            System.out.println(gest.depot.getModids().size() + " mods");

            Pattern regex = recherche != null ? Pattern.compile(recherche) : null;
            Depot dep = installes ? gest.installation : gest.depot;
            for (String modid : dep.getModids()) {
                if (regex != null && !regex.matcher(modid).find())
                    continue;

                System.out.println(modid);
                for (ModVersion mv : dep.getModVersions(modid)) {
                    System.out.println(String.format("\t+ %s [%s]", mv.version, mv.mcversion));
                }
            }

            return 0;
        }
    }
}
