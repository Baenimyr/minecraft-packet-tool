package McForgeMods.solveur;

import McForgeMods.Version;
import McForgeMods.VersionIntervalle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class SolveurTest {
	private Solveur<String, Version> solveur;
	
	@BeforeEach
	void setUp() {
		solveur = new Solveur<>();
		
		solveur.ajoutVariable("libcrypt1", Arrays.asList(null, new Version(4, 4, 16), new Version(4, 4, 10)));
		solveur.ajoutVariable("libgcc-s1", Arrays.asList(null, new Version(10, 2, 0), new Version(10, 0, 0)));
		
		solveur.ajoutVariable("libc6", Arrays.asList(null, new Version(2, 32, 0), new Version(2, 31, 0)));
		solveur.ajoutContrainte(new ContrainteDependance("libc6", new Version(2, 32, 0), "libcrypt1",
				new VersionIntervalle(new Version(4, 4, 10), null)));
		solveur.ajoutContrainte(
				new ContrainteDependance("libc6", new Version(2, 32, 0), "libgcc-s1", VersionIntervalle.ouvert()));
		solveur.ajoutContrainte(new ContrainteDependance("libc6", new Version(2, 31, 0), "libcrypt1",
				new VersionIntervalle(new Version(4, 4, 10), null)));
		solveur.ajoutContrainte(
				new ContrainteDependance("libc6", new Version(2, 31, 0), "libgcc-s1", VersionIntervalle.ouvert()));
		
		solveur.ajoutVariable("make",
				Arrays.asList(null, new Version(4, 3, 0), new Version(4, 2, 1), new Version(4, 1, 0)));
		solveur.ajoutContrainte(new ContrainteDependance("make", new Version(4, 3, 0), "libc6",
				new VersionIntervalle(new Version(2, 27, 0), null)));
		solveur.ajoutContrainte(new ContrainteDependance("make", new Version(4, 2, 1), "libc6",
				new VersionIntervalle(new Version(2, 27, 0), null)));
		solveur.ajoutContrainte(new ContrainteDependance("make", new Version(4, 1, 0), "libc6",
				new VersionIntervalle(new Version(2, 27, 0), null)));
		
		solveur.ajoutVariable("thunderbird",
				Arrays.asList(null, new Version(78, 3, 2), new Version(68, 7, 0), new Version(52, 7, 0),
						new Version(38, 6, 0)));
		solveur.ajoutContrainte(new ContrainteDependance("thunderbird", new Version(78, 3, 2), "libc6",
				new VersionIntervalle(new Version(2, 32, 0), null)));
		solveur.ajoutContrainte(new ContrainteDependance("thunderbird", new Version(78, 3, 2), "libgcc-s1",
				new VersionIntervalle(new Version(3, 3, 0), null)));
		solveur.ajoutContrainte(new ContrainteDependance("thunderbird", new Version(68, 7, 0), "libc6",
				new VersionIntervalle(new Version(2, 30, 0), null)));
		solveur.ajoutContrainte(new ContrainteDependance("thunderbird", new Version(68, 7, 0), "libgcc-s1",
				new VersionIntervalle(new Version(3, 3, 0), null)));
		solveur.ajoutContrainte(new ContrainteDependance("thunderbird", new Version(52, 7, 0), "libc6",
				new VersionIntervalle(new Version(2, 27, 0), null)));
		solveur.ajoutContrainte(new ContrainteDependance("thunderbird", new Version(38, 6, 0), "libc6",
				new VersionIntervalle(new Version(2, 30, 0), null)));
		
		solveur.ajoutVariable("thunderbird-locale-fr",
				Arrays.asList(null, new Version(78, 3, 2), new Version(68, 7, 0), new Version(52, 7, 0),
						new Version(38, 6, 0)));
		solveur.ajoutContrainte(new ContrainteDependance("thunderbird-locale-fr", new Version(78, 3, 2), "thunderbird",
				new VersionIntervalle(new Version(78, 3, 2), new Version(78, 3, 3))));
		solveur.ajoutContrainte(new ContrainteDependance("thunderbird-locale-fr", new Version(68, 7, 0), "thunderbird",
				new VersionIntervalle(new Version(68, 7, 0), new Version(68, 7, 1))));
		solveur.ajoutContrainte(new ContrainteDependance("thunderbird-locale-fr", new Version(52, 7, 0), "thunderbird",
				new VersionIntervalle(new Version(52, 7, 0), new Version(52, 7, 1))));
		solveur.ajoutContrainte(new ContrainteDependance("thunderbird-locale-fr", new Version(38, 6, 0), "thunderbird",
				new VersionIntervalle(new Version(38, 6, 0), new Version(38, 6, 1))));
	}
	
	@Test
	void coherenceVide() {
		assertTrue(solveur.coherence());
		
		assertEquals(3, solveur.domaineVariable("libcrypt1").size());
		assertEquals(3, solveur.domaineVariable("libgcc-s1").size());
		assertEquals(3, solveur.domaineVariable("libc6").size());
		assertEquals(4, solveur.domaineVariable("make").size());
		assertEquals(5, solveur.domaineVariable("thunderbird").size());
		assertEquals(5, solveur.domaineVariable("thunderbird-locale-fr").size());
	}
	
	@Test
	void coherenceAscendanteLibc6() {
		solveur.domaineVariable("libc6").reduction(new Version(2, 31, 0));
		assertTrue(solveur.coherence());
		
		assertEquals(1, solveur.domaineVariable("libc6").size());
		assertEquals(4, solveur.domaineVariable("make").size());
		assertEquals(4, solveur.domaineVariable("thunderbird").size());
		assertEquals(4, solveur.domaineVariable("thunderbird-locale-fr").size());
	}
	
	@Test
	void coherenceAscendanteThunderbird() {
		solveur.domaineVariable("thunderbird").reduction(new Version(68, 7, 0));
		assertTrue(solveur.coherence());
		
		assertEquals(3, solveur.domaineVariable("libcrypt1").size());
		assertEquals(3, solveur.domaineVariable("libgcc-s1").size());
		assertEquals(3, solveur.domaineVariable("libc6").size());
		assertEquals(4, solveur.domaineVariable("make").size());
		assertEquals(1, solveur.domaineVariable("thunderbird").size());
		assertEquals(2, solveur.domaineVariable("thunderbird-locale-fr").size());
	}
	
	@Test
	void resolutionVide() {
		assertTrue(solveur.resolution());
		assertEquals(1, solveur.domaineVariable("libcrypt1").size());
		assertEquals(1, solveur.domaineVariable("libgcc-s1").size());
		assertEquals(1, solveur.domaineVariable("libc6").size());
		assertEquals(1, solveur.domaineVariable("make").size());
		assertEquals(1, solveur.domaineVariable("thunderbird").size());
		assertEquals(1, solveur.domaineVariable("thunderbird-locale-fr").size());
	}
	
	@Test
	void resolutionAscendanteLibc6() {
		solveur.domaineVariable("libc6").reduction(new Version(2, 31, 0));
		assertTrue(solveur.resolution());
		
		assertEquals(1, solveur.domaineVariable("libc6").size());
		assertEquals(1, solveur.domaineVariable("make").size());
		assertEquals(1, solveur.domaineVariable("thunderbird").size());
		assertEquals(1, solveur.domaineVariable("thunderbird-locale-fr").size());
	}
	
	@Test
	void resolutionAscendanteThunderbird() {
		solveur.domaineVariable("thunderbird").reduction(new Version(68, 7, 0));
		assertTrue(solveur.resolution());
		
		assertEquals(1, solveur.domaineVariable("libcrypt1").size());
		assertEquals(1, solveur.domaineVariable("libgcc-s1").size());
		assertEquals(1, solveur.domaineVariable("libc6").size());
		assertEquals(1, solveur.domaineVariable("make").size());
		assertEquals(1, solveur.domaineVariable("thunderbird").size());
		assertEquals(1, solveur.domaineVariable("thunderbird-locale-fr").size());
		assertNotNull(solveur.domaineVariable("libcrypt1").get(0));
		assertNotNull(solveur.domaineVariable("libgcc-s1").get(0));
		assertNotNull(solveur.domaineVariable("thunderbird").get(0));
		assertNull(solveur.domaineVariable("thunderbird-locale-fr").get(0));
	}
	
	@Test
	void contrainteConflit() {
		solveur.ajoutVariable("gmail", Collections.singleton(new Version(17, 0, 0)));
		solveur.ajoutContrainte(
				new ContrainteConflit<>("gmail", new Version(17, 0, 0), "thunderbird", VersionIntervalle.ouvert()));
		assertTrue(solveur.resolution());
		assertEquals(1, solveur.domaineVariable("thunderbird").size());
		assertNull(solveur.domaineVariable("thunderbird").get(0));
	}
	
	@Test
	void conflitInsatifiable() {
		solveur.ajoutVariable("gmail", Collections.singleton(new Version(17, 0, 0)));
		solveur.ajoutContrainte(
				new ContrainteConflit<>("gmail", new Version(17, 0, 0), "thunderbird", VersionIntervalle.ouvert()));
		solveur.domaineVariable("thunderbird").remove(null);
		solveur.marquerVariable("thunderbird");
		
		assertFalse(solveur.resolution());
	}
}