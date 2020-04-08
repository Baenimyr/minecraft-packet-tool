package McForgeMods;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Une intervalle de versions englobe toutes les versions comprises entre les deux bornes.
 * <p>
 * Les bornes peuvent être inclusives ou exclusives. Si une version de borne est nulle, alors l'intervalle est ouverte
 * sur ce côté. Une intervalle doit respecter le format suivant:
 * <ul>
 *     <li>[v1,) pour v1 inclus sans limite supérieure</li>
 *     <li>(v1,v2): entre v1 et v2 exclus</li>
 *     <li>[,v2) = (,v2): inférieur à v2 exclus</li>
 *     <li>et les variantes d'inclusion/exclusion</li>
 *     <li>v1: voir {@link #read(String)}</li>
 * </ul>
 */
public class VersionIntervalle {
	public static final VersionIntervalle ouvert = new VersionIntervalle(null, null);
	
	Version minimum, maximum;
	boolean inclut_min, inclut_max;
	
	public VersionIntervalle() {
		this.minimum = this.maximum = null;
		this.inclut_min = this.inclut_max = false;
	}
	
	public VersionIntervalle(final VersionIntervalle v) {
		this.minimum = v.minimum;
		this.maximum = v.maximum;
		this.inclut_min = v.inclut_min;
		this.inclut_max = v.inclut_max;
	}
	
	public VersionIntervalle(Version minimum, Version maximum) {
		this.minimum = minimum;
		this.maximum = maximum;
		this.inclut_min = true;
		this.inclut_max = false;
	}
	
	public VersionIntervalle(Version v) {
		this.minimum = this.maximum = v;
		this.inclut_min = this.inclut_max = true;
	}
	
	/**
	 * Crée une dépendance n'acceptant qu'une seule version. Par exemple la présence d'un mod déjà installé, contraint
	 * la résolution de version à celle disponible.
	 */
	public VersionIntervalle(Version version, int precision) {
		this.minimum = version;
		this.maximum = new Version(version);
		this.maximum.set(precision, this.maximum.get(precision) + 1);
		this.inclut_min = true;
		this.inclut_max = false;
	}
	
	/**
	 * Lit une intervalle de version à partir d'une chaîne de caractère.
	 * <p>
	 * Le format des versions doit correspondre au format définit par {@link Version}. Si une seule version est fournie
	 * et qu'il n'y a pas de symboles d'intervalle ('(', ')', '[',']'), alors l'intervalle créée inclue la version
	 * fournie jusqu'à la version supérieure exclue. La version supérieure dépend du nombre d'éléments précisés pour la
	 * version, même nul. Ainsi
	 * <ul>
	 *     <li>1.2 -> [1.2,1.3)</li>
	 *     <li>1.2.0 -> [1.2,1.2.1)</li>
	 * </ul>
	 *
	 * @param contraintes texte de l'interval
	 * @throws VersionIntervalleFormatException si le format n'est pas respecté.
	 */
	public static VersionIntervalle read(String contraintes) throws VersionIntervalleFormatException {
		Version minimum = null, maximum = null;
		int precision_minimum = 0;
		boolean inclut_min = true, inclut_max = false;
		
		boolean intervalle = false;
		int pos = 0;
		if (!Character.isDigit(contraintes.charAt(pos))) {
			if (contraintes.charAt(pos) != '(' && contraintes.charAt(pos) != '[')
				throw new VersionIntervalleFormatException(contraintes + " commence avec un caractère invalide.");
			
			inclut_min = contraintes.charAt(pos) == '[';
			pos++;
			intervalle = true;
		}
		
		if (!intervalle || contraintes.charAt(pos) != ',') {
			Version.VersionBuilder b1 = new Version.VersionBuilder(contraintes, pos);
			int fin = b1.read();
			if (fin > pos) minimum = b1.version();
			precision_minimum = b1.precision;
			pos = fin;
		}
		
		if (intervalle) {
			if (pos >= contraintes.length() || contraintes.charAt(pos) != ',')
				throw new VersionIntervalleFormatException(
						String.format("L'intervalle n'est pas fermée: '%s'", contraintes));
			
			pos++;
			if (contraintes.charAt(pos) == ')') {
				inclut_max = false;
				pos++;
			} else if (contraintes.charAt(pos) == ']') {
				inclut_max = true;
				pos++;
			} else {
				Version.VersionBuilder b2 = new Version.VersionBuilder(contraintes, pos);
				int fin = b2.read();
				if (fin > pos) maximum = b2.version();
				pos = fin;
				if (pos >= contraintes.length() || (contraintes.charAt(pos) != ')' && contraintes.charAt(pos) != ']'))
					throw new VersionIntervalleFormatException(
							String.format("Mauvaise façon de fermer une intervalle: '%s'", contraintes));
				inclut_max = contraintes.charAt(pos++) == ']';
			}
		} if (contraintes.length() != pos) throw new VersionIntervalleFormatException(contraintes);
		
		if (minimum == null && maximum == null) return VersionIntervalle.ouvert;
		else if (intervalle) {
			VersionIntervalle v = new VersionIntervalle(minimum, maximum);
			v.inclut_min = inclut_min;
			v.inclut_max = inclut_max;
			return v;
		} else {
			return new VersionIntervalle(minimum, precision_minimum);
		}
	}
	
	/**
	 * Décompose une chaine de caractère réprésentant un mod et une intervalle de version.
	 * <p>
	 * Si plusieurs même identifiant de mod apparaissent dans la liste, seule l'intersection des intervalles est
	 * conservée. Le texte doit être sous la forme <i>modid</i>[@<i>intervalle</i>]. L'intervalle est en option donc si
	 * aucune intervalle n'est précisée, l'intervalle est nulle.
	 * <p>
	 * Exemples:
	 * <ul>
	 *     <li>modid</li>
	 *     <li>modid@2.3</li>
	 *     <li>modid@[2.3,)</li>
	 *     <li>modid@(,4.6]</li>
	 * </ul>
	 *
	 * @param entree: une liste de mod
	 * @return une map {modid -> intervalle}
	 * @throws IllegalArgumentException si la syntaxe est incorrecte.
	 */
	public static Map<String, VersionIntervalle> lectureDependances(Iterable<?> entree)
			throws IllegalArgumentException {
		final Map<String, VersionIntervalle> resultat = new HashMap<>();
		for (Object o : entree) {
			final String texte = o.toString();
			int pos = 0;
			StringBuilder modid_builder = new StringBuilder();
			VersionIntervalle versionIntervalle = VersionIntervalle.ouvert;
			
			while (pos < texte.length()) {
				char c = texte.charAt(pos);
				if (c == ',') {
					final String modid = modid_builder.toString().toLowerCase();
					if (resultat.containsKey(modid) && resultat.get(modid) != null) {
						resultat.get(modid).intersection(versionIntervalle);
					} else resultat.put(modid, versionIntervalle);
					
					modid_builder = new StringBuilder();
					versionIntervalle = VersionIntervalle.ouvert;
				} else if (c == '@' && modid_builder.length() > 0) {
					pos++;
					if (pos >= texte.length()) {
						throw new VersionIntervalleFormatException("Intervalle non spécifiée dans " + texte);
					}
					
					StringBuilder intervalle = new StringBuilder();
					c = texte.charAt(pos);
					while (pos < texte.length() && (Character.isDigit(c) || c == ',' || c == '[' || c == ']' || c == '('
							|| c == ')' || c == '.' || c == '-' || c == '+')) {
						intervalle.append(c);
						pos++;
						if (pos < texte.length()) c = texte.charAt(pos);
					}
					versionIntervalle = read(intervalle.toString());
					
				} else if (Character.isAlphabetic(c) || Character.isDigit(c)) {
					modid_builder.append(c);
				} else {
					throw new VersionIntervalleFormatException(texte);
				}
				pos++;
			}
			
			if (modid_builder.length() > 0) {
				resultat.put(modid_builder.toString().toLowerCase(), versionIntervalle);
			}
		}
		return resultat;
	}
	
	/**
	 * Cette intervalle devient l'intersection des deux intervalles.
	 *
	 * @param d: une autre intervalle.
	 */
	public void intersection(VersionIntervalle d) {
		if (Objects.isNull(d)) return;
		if (Objects.equals(minimum, d.minimum)) inclut_min = !(!inclut_min || !d.inclut_min);
		else if (minimum == null || (d.minimum != null && d.minimum.compareTo(minimum) > 0)) {
			minimum = d.minimum;
			inclut_min = d.inclut_min;
		}
		
		if (Objects.equals(maximum, d.maximum)) inclut_max = !(!inclut_max || !d.inclut_max);
		else if (maximum == null || (d.maximum != null && d.maximum.compareTo(maximum) < 0)) {
			maximum = d.maximum;
			inclut_max = d.inclut_max;
		}
	}
	
	/**
	 * @return {@code true} si la version est comprise dans l'intervalle.
	 */
	public boolean correspond(Version version) {
		return (minimum == null || version.compareTo(minimum) >= (inclut_min ? 0 : 1)) && (maximum == null
				|| version.compareTo(maximum) <= (inclut_max ? 0 : -1));
	}
	
	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		VersionIntervalle that = (VersionIntervalle) o;
		return inclut_min == that.inclut_min && inclut_max == that.inclut_max && Objects.equals(minimum, that.minimum)
				&& Objects.equals(maximum, that.maximum);
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(minimum, maximum, inclut_min, inclut_max);
	}
	
	@Override
	public String toString() {
		return (minimum != null ? (inclut_min ? '[' : '(') + minimum.toString() : '(') + "," + (maximum != null ?
				maximum.toString() + (inclut_max ? ']' : ')') : ')');
	}
	
	public static class VersionIntervalleFormatException extends IllegalArgumentException {
		public VersionIntervalleFormatException(String texte) {
			super(texte);
		}
	}
}
