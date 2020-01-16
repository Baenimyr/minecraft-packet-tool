package McForgeMods;

import java.util.Objects;

public class VersionIntervalle {
    Version minimum, maximum;
    boolean inclut_min, inclut_max;

    public VersionIntervalle() {
        this(null, null);
    }

    public VersionIntervalle(Version minimum, Version maximum) {
        this.minimum = minimum;
        this.maximum = maximum;
        this.inclut_min = true;
        this.inclut_max = false;
    }

    /**
     * Crée une dépendance n'acceptant qu'une seule version.
     * Par exemple la présence d'un mod déjà installé, contraint la résolution de version à celle disponible.
     */
    public VersionIntervalle(Version version) {
        this.minimum = version;
        this.maximum = new Version(version);
        int p = precision(this.maximum);
        this.maximum.set(p, this.maximum.get(p) + 1);
        this.inclut_min = true;
        this.inclut_max = false;
    }

    public static VersionIntervalle read(String contraintes) throws VersionIntervalleFormatException {
        VersionIntervalle d = new VersionIntervalle();

        boolean intervalle = false;
        int pos = 0;
        if (!Character.isDigit(contraintes.charAt(pos))) {
            if (contraintes.charAt(pos) != '(' && contraintes.charAt(pos) != '[')
                throw new VersionIntervalleFormatException(contraintes + " commence avec un caractère invalide.");
            
            d.inclut_min = contraintes.charAt(pos) == '[';
            pos++;
            intervalle = true;
        }

        int virgule = pos;
        while (virgule < contraintes.length()
                && (contraintes.charAt(virgule) == '.' || Character.isDigit(contraintes.charAt(virgule))))
            virgule++;

        if (virgule > pos)
            d.minimum = Version.read(contraintes.substring(pos, virgule));
        pos = virgule;

        if (intervalle) {
            if (pos >= contraintes.length() || contraintes.charAt(pos) != ',')
                throw new VersionIntervalleFormatException(String.format("L'intervalle n'est pas fermée: '%s'",
                        contraintes));
            
            virgule = ++pos;
            while (virgule < contraintes.length()
                    && (contraintes.charAt(virgule) == '.' || Character.isDigit(contraintes.charAt(virgule))))
                virgule++;
    
            if (virgule > pos) {
                d.maximum = Version.read(contraintes.substring(pos, virgule));
                pos = virgule;
            }
    
            if (pos >= contraintes.length() || (contraintes.charAt(pos) != ')' && contraintes.charAt(pos) != ']'))
                throw new VersionIntervalleFormatException(
                        String.format("Mauvaise façon de fermer une intervalle: '%s'", contraintes));
            d.inclut_max = contraintes.charAt(pos++) == ']';
        }
        if (contraintes.length() != pos)
            throw new VersionIntervalleFormatException(contraintes);
        return d;
    }

    public void fusion(VersionIntervalle d) {
        if (Objects.equals(minimum, d.minimum))
            inclut_min = !(!inclut_min || !d.inclut_min);
        else if (minimum == null)
            minimum = d.minimum;
        else if (d.minimum != null) {
            minimum = minimum.compareTo(d.minimum) > 0 ? minimum : d.minimum;
        }

        if (Objects.equals(maximum, d.maximum))
            inclut_max = !(!inclut_max || !d.inclut_max);
        else if (maximum == null)
            maximum = d.maximum;
        else if (d.maximum != null)
            maximum = maximum.compareTo(d.maximum) < 0 ? maximum : d.maximum;
    }

    public boolean correspond(Version version) {
        return (minimum == null || version.compareTo(minimum) >= (inclut_min ? 0 : 1))
                && (maximum == null || version.compareTo(maximum) <= (inclut_max ? 0 : -1));
    }

    /** Par construction, tous les champs de sous-version sont remplis par 0.
     * Tous les champs sont utilisés pour l'égalité et la comparaison de Version, cependant dans le cas d'une intervalle,
     * il est interressant de laisser une certaine souplesse sur les derniers champs nuls.
     * @return l'index de la dernière sous-version non nulle. */
    public static int precision(Version version) {
        for (int i = version.size() - 1; i >= 0; i--) {
            if (version.get(i) != 0)
                return i;
        }
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VersionIntervalle that = (VersionIntervalle) o;
        return inclut_min == that.inclut_min &&
                inclut_max == that.inclut_max &&
                Objects.equals(minimum, that.minimum) &&
                Objects.equals(maximum, that.maximum);
    }

    @Override
    public int hashCode() {
        return Objects.hash(minimum, maximum, inclut_min, inclut_max);
    }

    @Override
    public String toString() {
        return (minimum != null ? (inclut_min ? '[' : '(') + minimum.toString() : '(') + ","
                + (maximum != null ? maximum.toString() + (inclut_max ? ']' : ')') : ')');
    }

    public static class VersionIntervalleFormatException extends IllegalArgumentException {
        public VersionIntervalleFormatException(String texte) {
            super("Interval non reconnue: '" + texte + "'");
        }
    }
}
