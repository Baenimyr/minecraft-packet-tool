package McForgeMods;

import McForgeMods.commandes.Show;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "forgemods", showDefaultValues = true, mixinStandardHelpOptions = true,
        subcommands = {Show.class, ForgeMods.Install.class, ForgeMods.Depot.class})
public class ForgeMods implements Runnable {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public void run() {
        throw new CommandLine.ParameterException(spec.commandLine(), "Missing required subcommand");
    }


    /**
     * Options commune aux fonctions utilisant un dépot et une installation minecraft.
     */
    public static class DossiersOptions {
        @CommandLine.Option(names = {"-d", "--depot"}, description = "Dépot local à utiliser", defaultValue = "~/.minecraft/forgemods")
        public Path depot;

        @CommandLine.Option(names = {"-m", "--minecraft"}, description = "Dossier minecraft (~/.minecraft)")
        public Path minecraft;
    }

    @CommandLine.Command(name = "install", mixinStandardHelpOptions = true,
            description = "Installe un mod et ses dépendances")
    static class Install implements Callable<Integer> {
        @CommandLine.Mixin
        DossiersOptions dossiers;

        @CommandLine.Parameters(arity = "0..*", description = "Liste d'identifiants")
        String[] modid;

        @CommandLine.Option(names = {"--dependences"}, negatable = true,
                description = "Installe tous les autres mods nécessaires au bon fonctionnement de l'installation.")
        boolean dependances = true;

        @Override
        public Integer call() {
            System.err.println("Cette fonction n'est pas prête");
            return 1;
        }
    }

    @CommandLine.Command(name = "depot", mixinStandardHelpOptions = true,
            description = "Outil de gestion d'un dépot.\n" +
                    "Une installation minecraft peut être utilisée comme source de fichiers.")
    static class Depot {
        @CommandLine.Mixin
        DossiersOptions dossiers;
    }

    public static void main(String[] args) {
        CommandLine cl = new CommandLine(new ForgeMods());
        System.exit(cl.execute(args));
    }
}
