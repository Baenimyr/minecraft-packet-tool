package McForgeMods.solveur;

import McForgeMods.Version;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class DomaineTest {
	private Domaine<Version> domaine = null;
	
	@BeforeEach
	void creationDomaine() {
		domaine = new Domaine<>(
				Arrays.asList(new Version(3, 1, 0), new Version(3, 0, 2), new Version(3, 0, 1), new Version(2, 7, 0),
						new Version(4, 6, 7), new Version(2, 6, 7)));
	}
	
	@Test
	void testInit() {
		assertEquals(6, domaine.size());
		assertEquals(new Version(3, 0, 1), domaine.get(2));
	}
	
	@Test
	void testRemove() {
		assertFalse(domaine.remove(new Version(1, 13, 78)));
		assertTrue(domaine.remove(new Version(3, 1, 0)));
		assertEquals(5, domaine.size());
		assertEquals(new Version(3, 0, 2), domaine.get(0));
		assertEquals(new Version(2, 6, 7), domaine.get(4));
		assertFalse(domaine.contains(new Version(3, 1, 0)));
		assertTrue(domaine.contains(new Version(2, 7, 0)));
	}
	
	@Test
	void testReduction() {
		final Version selection = new Version(4, 6, 7);
		domaine.reduction(selection);
		assertEquals(1, domaine.size());
		assertTrue(domaine.contains(selection));
		assertFalse(domaine.contains(new Version(3, 0, 2)));
	}
	
	@Test
	void testPushPop() {
		domaine.push();
		assertTrue(domaine.remove(new Version(3, 1, 0)));
		domaine.pop();
		assertEquals(6, domaine.size());
		assertTrue(domaine.contains(new Version(3, 1, 0)));
	}
}