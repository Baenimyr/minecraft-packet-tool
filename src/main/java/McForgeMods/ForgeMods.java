package McForgeMods;

import McForgeMods.commandes.*;
import McForgeMods.depot.ArbreDependance;
import McForgeMods.depot.DepotInstallation;
import McForgeMods.depot.DepotLocal;
import McForgeMods.outils.Sources;
import picocli.CommandLine;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@CommandLine.Command(name = "forgemods", showDefaultValues = true, resourceBundle = "mcforgemods/lang/ForgeMods",
		mixinStandardHelpOptions = true,
		subcommands = {CommandeShow.class, CommandeListe.class, CommandeDepot.class, CommandeInstall.class,
				CommandeUpdate.class})
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
	
	@CommandLine.Command(name = "add-repository")
	public int ajout_repo(@CommandLine.Parameters(index = "0", arity = "1..n", paramLabel = "url") List<String> urls,
			@CommandLine.Option(names = {"-d", "--depot"}) Path depot) {
		final DepotLocal depotLocal = new DepotLocal(depot);
		final Sources sources = new Sources();
		
		depotLocal.dossier.toFile().mkdirs();
		final File fichier = depotLocal.dossier.resolve("sources.txt").toFile();
		if (fichier.exists()) {
			try (FileInputStream input = new FileInputStream(fichier)) {
				sources.importation(input);
			} catch (IOException ignored) {
				return -1;
			}
		}
		
		try (FileOutputStream output = new FileOutputStream(fichier, true);
			 OutputStreamWriter bos = new OutputStreamWriter(output); BufferedWriter bw = new BufferedWriter(bos)) {
			for (String url : urls) {
				try {
					URI u = new URI(url);
					if (url.endsWith(".gz")) u = new URI("gz:" + u);
					
					if (!sources.urls().contains(u)) {
						bw.newLine();
						bw.write(u.toString());
					}
				} catch (URISyntaxException m) {
					System.err.printf("URISyntax Error: '%s'%n", url);
				}
			}
		} catch (IOException i) {
			i.printStackTrace();
			return -1;
		}
		
		return 0;
	}
	
	@CommandLine.Command(name = "search")
	public int commandeSearch(@CommandLine.Mixin Help help, @CommandLine.Option(names = {"-d", "--depot"}) Path depot,
			@CommandLine.Option(names = {"-e", "--regex"}) boolean regex,
			@CommandLine.Parameters(paramLabel = "search", arity = "1") String recherche) {
		final DepotLocal depotLocal = new DepotLocal(depot);
		try {
			depotLocal.importation();
		} catch (IOException io) {
			System.err.println("Erreur de lecture du dépôt.");
			return 1;
		}
		
		final HashSet<String> modids = new HashSet<>();
		if (regex) {
			Pattern schema = Pattern.compile(recherche);
			for (String modid : depotLocal.getModids()) {
				Matcher m_modid = schema.matcher(modid);
				if (m_modid.find()) {
					modids.add(modid);
					continue;
				}
			}
		} else {
			String recherche_l = recherche.toLowerCase();
			for (String modid : depotLocal.getModids()) {
				if (modid.contains(recherche_l)) {
					modids.add(modid);
					continue;
				}
			}
			depotLocal.getModids().stream().filter(modid -> modid.contains(recherche)).forEach(modids::add);
		}
		
		modids.stream().sorted(String::compareTo).forEach(modid -> {
			Optional<PaquetMinecraft> modVersion = depotLocal.getModVersions(modid).stream()
					.max(Comparator.comparing(mv -> mv.version));
			modVersion.ifPresent(mod -> System.out.printf("\u001B[32m%s\u001B[0m \"%s\"%n", mod.modid, mod.version));
		});
		
		return 0;
	}
	
	@CommandLine.Command(name = "depends")
	public int depends(
			@CommandLine.Parameters(index = "0", arity = "0..n", descriptionKey = "mods") ArrayList<String> mods,
			@CommandLine.Mixin ForgeMods.DossiersOptions dossiers,
			@CommandLine.Option(names = {"--missing"}, defaultValue = "false") boolean missing,
			@CommandLine.Option(names = {"-a", "--all"}) boolean all, @CommandLine.Mixin ForgeMods.Help help) {
		final DepotLocal depotLocal = new DepotLocal(dossiers.depot);
		final DepotInstallation depotInstallation = new DepotInstallation(depotLocal, dossiers.minecraft);
		
		try {
			depotLocal.importation();
		} catch (IOException e) {
			System.err.println("Erreur de lecture du dépôt.");
			return 1;
		}
		depotInstallation.analyseDossier();
		
		// Liste des versions pour lesquels chercher les dépendances.
		List<PaquetMinecraft> listeRecherche;
		
		if (all) {
			listeRecherche = depotInstallation.getModids().stream().map(depotInstallation::informations)
					.map(ins -> depotLocal.getModVersion(ins.modid, ins.version)).filter(Optional::isPresent)
					.map(Optional::get).collect(Collectors.toList());
		} else if (mods != null && mods.size() > 0) {
			final List<PaquetMinecraft> resultat = new ArrayList<>();
			final Map<String, VersionIntervalle> recherche;
			
			try {
				recherche = VersionIntervalle.lectureDependances(mods);
			} catch (IllegalArgumentException iae) {
				System.err.println("[ERROR] " + iae.getMessage());
				return 1;
			}
			for (Map.Entry<String, VersionIntervalle> entry : recherche.entrySet()) {
				String modid = entry.getKey();
				VersionIntervalle version = entry.getValue();
				if (depotLocal.contains(modid)) {
					Optional<PaquetMinecraft> trouvee = depotLocal.getModVersions(modid).stream()
							.filter(modVersion -> version.equals(VersionIntervalle.ouvert()) || version
									.correspond(modVersion.version)).max(Comparator.comparing(mv -> mv.version));
					if (trouvee.isPresent()) resultat.add(trouvee.get());
					else {
						System.err.printf("Version inconnue pour '%s': '%s'%n", modid, version);
						return 3;
					}
				} else {
					System.err.printf("Modid inconnu: '%s'%n", modid);
					return 3;
				}
			}
			listeRecherche = resultat;
		} else {
			System.err.println("Nécessite une liste de travail.");
			return 4;
		}
		
		// Liste complète des dépendances nécessaire pour la liste des mods présent.
		ArbreDependance arbre_dependances = new ArbreDependance(depotLocal, listeRecherche);
		arbre_dependances.resolution();
		Map<String, VersionIntervalle> liste;
		if (missing) {
			liste = depotInstallation.dependancesAbsentes(arbre_dependances.requis());
			System.out.printf("%d absents%n", liste.size());
		} else {
			liste = arbre_dependances.requis();
			System.out.printf("%d dépendances%n", liste.size());
		}
		
		ArrayList<String> modids = new ArrayList<>(liste.keySet());
		modids.sort(String::compareTo);
		for (String dep : modids) {
			System.out.println(dep + " " + liste.get(dep));
		}
		return 0;
	}
	
	@CommandLine.Command(name = "mark")
	public int mark(@CommandLine.Option(names = {"-d", "--depot"}) Path depot,
			@CommandLine.Option(names = {"-m", "--minecraft"}) Path minecraft,
			@CommandLine.Parameters(arity = "1", paramLabel = "action") MarkAction action,
			@CommandLine.Parameters(arity = "1..n", paramLabel = "mods") ArrayList<String> mods) {
		final DepotLocal depotLocal = new DepotLocal(depot);
		final DepotInstallation depotInstallation = new DepotInstallation(depotLocal, minecraft);
		depotInstallation.analyseDossier();
		Map<String, VersionIntervalle> versions = VersionIntervalle.lectureDependances(mods);
		for (String modid : versions.keySet()) {
			if (depotInstallation.contains(modid)) {
				DepotInstallation.Installation mv = depotInstallation.informations(modid);
				if (versions.get(modid).correspond(mv.version)) {
					if (action == MarkAction.manual || action == MarkAction.auto) {
						if (mv.verrou) {
							System.err.printf("%s est verrouillé%n", mv);
						} else mv.manuel = action == MarkAction.manual;
					} else mv.verrou = action == MarkAction.lock;
				} else {
					System.err.printf("Le mod %s@%s n'est pas installé", modid, versions.get(modid));
				}
			}
		}
		
		try {
			depotInstallation.statusSauvegarde();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return 0;
	}
	
	private enum MarkAction {
		manual,
		auto,
		lock,
		unlock
	}
	
	public static void main(String[] args) {
		CommandLine cl = new CommandLine(new ForgeMods());
		System.exit(cl.execute(args));
	}
	
	/**
	 * Options commune aux fonctions utilisant un dépot et une installation minecraft.
	 */
	public static class DossiersOptions {
		/**
		 * Spécifie le dossier de dépot à utiliser.
		 */
		@CommandLine.Option(names = {"-d", "--depot"}, description = "Dépot local à utiliser")
		public Path depot = null;
		
		/**
		 * Spécifie l'installation minecraft à utiliser. Il peut en exister d'autres de ~/.minecraft.
		 */
		@CommandLine.Option(names = {"-m", "--minecraft"}, description = "Dossier minecraft (~/.minecraft)")
		public Path minecraft = null;
	}
}
