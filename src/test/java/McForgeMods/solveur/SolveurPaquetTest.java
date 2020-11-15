package McForgeMods.solveur;

import McForgeMods.PaquetMinecraft;
import McForgeMods.Version;
import McForgeMods.VersionIntervalle;
import McForgeMods.depot.Depot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SolveurPaquetTest {
	static final VersionIntervalle mc_1_12 = new VersionIntervalle(new Version(1, 12, 2), new Version(1, 13, 0), true,
			false);
	Depot depot;
	
	@BeforeEach
	void generationDepot() {
		depot = new Depot();
		
		final PaquetMinecraft lib_core_1 = new PaquetMinecraft("core", new Version(1, 0, 0), mc_1_12);
		final PaquetMinecraft lib_core_2 = new PaquetMinecraft("core", new Version(1, 1, 0), mc_1_12);
		final PaquetMinecraft lib_core_3 = new PaquetMinecraft("core", new Version(2, 0, 1), mc_1_12);
		
		final PaquetMinecraft mod_core_1 = new PaquetMinecraft("modcore", new Version(1, 0, 0), mc_1_12);
		final PaquetMinecraft mod_core_2 = new PaquetMinecraft("modcore", new Version(1, 1, 0), mc_1_12);
		final PaquetMinecraft mod_core_3 = new PaquetMinecraft("modcore", new Version(2, 0, 0), mc_1_12);
		mod_core_1.ajoutModRequis("core", VersionIntervalle.read("[1.0,2)"));
		mod_core_2.ajoutModRequis("core", VersionIntervalle.read("[1.1,2)"));
		mod_core_3.ajoutModRequis("core", VersionIntervalle.read("[2.0,3)"));
		
		final PaquetMinecraft mod_ext_1 = new PaquetMinecraft("modext1", new Version(1, 2, 0), mc_1_12);
		mod_ext_1.ajoutModRequis("modcore", VersionIntervalle.read("[1.0,2)"));
		mod_ext_1.ajoutModRequis("core", VersionIntervalle.read("[1.0,1.1)"));
		
		final PaquetMinecraft mod_ext_2 = new PaquetMinecraft("modext2", new Version(0, 6, 0), mc_1_12);
		mod_ext_2.ajoutModRequis("modcore", VersionIntervalle.read("[1.0,2)"));
		mod_ext_2.ajoutModRequis("core", new VersionIntervalle(new Version(1, 1, 0), new Version(2, 0, 0)));
		
		depot.ajoutModVersion(lib_core_1);
		depot.ajoutModVersion(lib_core_2);
		depot.ajoutModVersion(lib_core_3);
		depot.ajoutModVersion(mod_core_1);
		depot.ajoutModVersion(mod_core_2);
		depot.ajoutModVersion(mod_core_3);
		depot.ajoutModVersion(mod_ext_1);
		depot.ajoutModVersion(mod_ext_2);
	}
	
	@Test
	void resolutionVide() {
		final HashMap<String, VersionIntervalle> demande = new HashMap<>();
		final SolveurPaquet solveur = new SolveurPaquet(depot, new Version(1, 12, 2), new Version(14, 23, 5, 2854));
		solveur.initialisationMod("core");
		solveur.initialisationMod("modcore");
		solveur.initialisationMod("modext1");
		solveur.initialisationMod("modext2");
		
		solveur.init(demande);
		solveur.coherence();
		assertEquals(3 + 1, solveur.domaineVariable("core").size());
		assertEquals(3 + 1, solveur.domaineVariable("modcore").size());
		assertEquals(1 + 1, solveur.domaineVariable("modext1").size());
		assertEquals(1 + 1, solveur.domaineVariable("modext2").size());
	}
	
	@Test
	void resolutionMod() {
		final HashMap<String, VersionIntervalle> demande = new HashMap<>();
		demande.put("modcore", new VersionIntervalle(new Version(1, 0, 0), new Version(2, 0, 0)));
		final SolveurPaquet solveur = new SolveurPaquet(depot, new Version(1, 12, 2), new Version(14, 23, 5, 2854));
		solveur.initialisationMod("core");
		solveur.initialisationMod("modcore");
		solveur.initialisationMod("modext1");
		solveur.initialisationMod("modext2");
		solveur.init(demande);
		solveur.coherence();
		
		assertEquals(3 + 1, solveur.domaineVariable("core").size());
		assertEquals(2, solveur.domaineVariable("modcore").size());
		assertEquals(2, solveur.domaineVariable("modext1").size());
		assertEquals(2, solveur.domaineVariable("modext2").size());
	}
	
	@Test
	void contrainteDependance() {
		final HashMap<String, VersionIntervalle> demande = new HashMap<>();
		demande.put("core", new VersionIntervalle(new Version(1, 1, 0), new Version(1, 2, 0)));
		final SolveurPaquet solveur = new SolveurPaquet(depot, new Version(1, 12, 2), new Version(14, 23, 5, 2854));
		solveur.init(demande);
		solveur.initialisationMod("core");
		solveur.initialisationMod("modcore");
		solveur.initialisationMod("modext1");
		solveur.initialisationMod("modext2");
		
		solveur.coherence();
		System.out.println("[Test] solveur init");
		
		assertEquals(1, solveur.domaineVariable("core").size());
		assertTrue(solveur.domaineVariable("core").contains(new Version(1, 1, 0)));
		assertEquals(2 + 1, solveur.domaineVariable("modcore").size());
		assertEquals(1, solveur.domaineVariable("modext1").size());
		assertEquals(2, solveur.domaineVariable("modext2").size());
	}
}
