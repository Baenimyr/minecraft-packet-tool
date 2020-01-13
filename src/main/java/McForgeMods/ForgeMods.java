package McForgeMods;

import McForgeMods.commandes.Show;
import McForgeMods.depot.DepotInstallation;
import McForgeMods.depot.DepotLocal;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Path;

@CommandLine.Command(name = "forgemods", showDefaultValues = true, mixinStandardHelpOptions = true,
        subcommands = {Show.class, ForgeMods.Depot.class})
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
        @CommandLine.Option(names = {"-d", "--depot"}, description = "Dépot local à utiliser")
        public Path depot = Path.of(System.getProperty("user.home")).resolve(".minecraft/forgemods");

        @CommandLine.Option(names = {"-m", "--minecraft"}, description = "Dossier minecraft (~/.minecraft)")
        public Path minecraft;
    }

    @CommandLine.Command(name = "install", mixinStandardHelpOptions = true,
            description = "Installe un mod et ses dépendances")
    public int installation(@CommandLine.Mixin DossiersOptions dossiers,
            @CommandLine.Parameters(arity = "0..*", description = "Liste d'identifiants") String[] modid,
            @CommandLine.Option(names = {"--dependences"}, negatable = true,
                    description = "Installe tous les autres mods nécessaires au bon fonctionnement de l'installation.") boolean dependances) {

        System.err.println("Cette fonction n'est pas prête");
        return 1;
    }

    /**
     * La fonction depot est responsable de toutes les interactions de modification du depot.
     * Elle est le seule capable de modifier les données.
     * <p>
     * Elle permet la lecture/ecriture des attributs des informations sauvegardées dans le dépot.
     * La sous-commande <i>import</i> associé avec une installation minecraft récupère les informations directement dans les <i>mcmod.info</i> des fichiers jar trouvés.
     */
    @CommandLine.Command(name = "depot", mixinStandardHelpOptions = true,
            description = "Outil de gestion d'un dépot.\n" + "Une installation minecraft peut être utilisée comme source de fichiers.")
    static class Depot implements Runnable {
        @CommandLine.Mixin
        DossiersOptions dossiers;

        @Override
        public void run() {

        }

        @CommandLine.Command(name = "import", mixinStandardHelpOptions = true,
                description = "Permet d'importer des informations présentes dans les fichiers mcmod.info des archives jar. " + "Utilise un dépot minecraft comme source des jars.")
        public int importation(
                @CommandLine.Parameters(index = "0", arity = "0..*", paramLabel = "modid") String[] modids,
                @CommandLine.Option(names = {"-a", "--all"}) boolean all, @CommandLine.Mixin DossiersOptions dossiers) {
            Path minecraft = DepotInstallation.resolutionDossierMinecraft(dossiers.minecraft);
            if (minecraft == null) {
                System.out.println("Impossible de trouver un dossier d'installation minecraft.");
                return 1;
            }

            DepotInstallation installation = new DepotInstallation(minecraft);
            installation.analyseDossier();
            DepotLocal depot = new DepotLocal(dossiers.depot);
            try {
                depot.importation();
            } catch (IOException i) {
                System.err.println("Erreur de lecture des informations du dépot.");
                return 1;
            }

            if (all) {
                for (String modid : installation.getModids()) {
                    for (ModVersion mv : installation.getModVersions(modid))
                        depot.ajoutModVersion(mv);
                }
                System.out.println(installation.getModids().size() + " mods importés");
            } else if (modids != null && modids.length > 0) {
                for (String modid : modids) {
                    if (installation.getModids().contains(modid)) {
                        for (ModVersion version : installation.getModVersions(modid))
                            depot.ajoutModVersion(version);
                    } else {
                        System.err.println("Modid non reconnu: '" + modid + "'");
                    }
                }
            } else {
                System.err.println("Il faut au moins un nom de mod à importer. Sinon utiliser l'option '--all'.");
                return 2;
            }

            try {
                depot.sauvegarde();
            } catch (IOException i) {
                System.err.println("Impossible de sauvegarder le dépot local à '" + depot.dossier + "'");
                return 1;
            }
            return 0;
        }
    }

    public static void main(String[] args) {
        CommandLine cl = new CommandLine(new ForgeMods());
        System.exit(cl.execute(args));
    }
}
