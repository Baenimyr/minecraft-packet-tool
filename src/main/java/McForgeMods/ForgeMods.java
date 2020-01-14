package McForgeMods;

import McForgeMods.commandes.CommandeDepot;
import McForgeMods.commandes.Show;
import picocli.CommandLine;

import java.nio.file.Path;

@CommandLine.Command(name = "forgemods", showDefaultValues = true, mixinStandardHelpOptions = true,
		subcommands = {Show.class, CommandeDepot.class})
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
		/**
		 * Spécifie le dossier de dépot à utiliser.
		 */
		@CommandLine.Option(names = {"-d", "--depot"}, description = "Dépot local à utiliser")
		public Path depot = Path.of(System.getProperty("user.home")).resolve(".minecraft/forgemods");
		
		/**
		 * Spécifie l'installation minecraft à utiliser. Il peut en exister d'autres de ~/.minecraft.
		 */
		@CommandLine.Option(names = {"-m", "--minecraft"}, description = "Dossier minecraft (~/.minecraft)")
		public Path minecraft;
	}
	
	@CommandLine.Command(name = "install", mixinStandardHelpOptions = true,
			description = "Installe un mod et ses dépendances")
	public int installation(@CommandLine.Mixin DossiersOptions dossiers,
			@CommandLine.Parameters(arity = "0..*", description = "Liste d'identifiants") String[] modid,
			@CommandLine.Option(names = {"--dependences"}, negatable = true,
					description = "Installe tous les autres mods nécessaires au bon fonctionnement de l'installation.")
					boolean dependances) {
		
		System.err.println("Cette fonction n'est pas prête");
		return 1;
	}
	
	public static void main(String[] args) {
		CommandLine cl = new CommandLine(new ForgeMods());
		System.exit(cl.execute(args));
	}
}
