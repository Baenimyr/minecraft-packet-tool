package McForgeMods;

import McForgeMods.commandes.CommandeDepot;
import McForgeMods.commandes.Show;
import McForgeMods.outils.Dossiers;
import picocli.CommandLine;

import java.io.*;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

@CommandLine.Command(name = "forgemods", showDefaultValues = true, mixinStandardHelpOptions = true,
		subcommands = {Show.class, CommandeDepot.class})
public class ForgeMods implements Runnable {
	
	@CommandLine.Spec
	CommandLine.Model.CommandSpec spec;
	
	@Override
	public void run() {
		throw new CommandLine.ParameterException(spec.commandLine(), "Missing required subcommand");
	}
	
	public static class Help {
		@CommandLine.Option(names = {"--help"}, usageHelp = true)
		boolean help;
	}
	
	@CommandLine.Command(name = "install", description = "Installe un mod et ses dépendances")
	public int installation(@CommandLine.Mixin Dossiers.DossiersOptions dossiers, @CommandLine.Mixin Help help,
			@CommandLine.Parameters(arity = "0..*", description = "Liste d'identifiants") String[] modids,
			@CommandLine.Option(names = {"--dependences"}, negatable = true,
					description = "Installe tous les autres mods nécessaires au bon fonctionnement de l'installation.")
					boolean dependances) {
		
		
		return 1;
	}
	
	@CommandLine.Command(name = "add-repository")
	public int ajout_repo(@CommandLine.Parameters(index = "0", arity = "1..n", paramLabel = "url") List<String> url,
			@CommandLine.Option(names = {"-d", "--depot"}) Path depot) {
		depot = Dossiers.dossierDepot(depot);
		final Collection<String> sources = new HashSet<>();
		
		final File fichier = depot.resolve("sources.txt").toFile();
		if (fichier.exists()) {
			try (FileInputStream input = new FileInputStream(fichier);
				 BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {
				reader.lines().map(String::toLowerCase).forEach(sources::add);
			} catch (IOException ignored) {
				return -1;
			}
		}
		
		url.stream().map(String::toLowerCase).forEach(sources::add);
		
		try (FileOutputStream output = new FileOutputStream(fichier);
			 OutputStreamWriter buff = new OutputStreamWriter(output);
			 BufferedWriter writer = new BufferedWriter(buff)) {
			for (String src : sources) {
				writer.write(src);
				writer.write("\n");
			}
		} catch (IOException ignored) {
			return -1;
		}
		
		return 0;
	}
	
	public static void main(String[] args) {
		CommandLine cl = new CommandLine(new ForgeMods());
		System.exit(cl.execute(args));
	}
}
