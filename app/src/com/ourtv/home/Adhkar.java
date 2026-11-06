package com.ourtv.home;

/**
 * Adhkar from Hisn al-Muslim, grouped as the family reads them: morning, evening, after prayer and
 * before sleep. Quran verses are written without diacritics so that no mark is ever wrong.
 * Each entry: text, how many times.
 */
final class Adhkar {
    static final String[] SECTIONS_AR = {"أذكار الصباح", "أذكار المساء", "بعد الصلاة", "قبل النوم"};
    static final String[] SECTIONS_EN = {"Morning", "Evening", "After prayer", "Before sleep"};

    private static final String KURSI = "الله لا إله إلا هو الحي القيوم، لا تأخذه سنة ولا نوم، له ما في السماوات وما في الأرض، "
            + "من ذا الذي يشفع عنده إلا بإذنه، يعلم ما بين أيديهم وما خلفهم، ولا يحيطون بشيء من علمه إلا بما شاء، "
            + "وسع كرسيه السماوات والأرض، ولا يؤوده حفظهما، وهو العلي العظيم";
    private static final String IKHLAS = "بسم الله الرحمن الرحيم\nقل هو الله أحد\nالله الصمد\nلم يلد ولم يولد\nولم يكن له كفوا أحد";
    private static final String FALAQ = "بسم الله الرحمن الرحيم\nقل أعوذ برب الفلق\nمن شر ما خلق\nومن شر غاسق إذا وقب\nومن شر النفاثات في العقد\nومن شر حاسد إذا حسد";
    private static final String NAS = "بسم الله الرحمن الرحيم\nقل أعوذ برب الناس\nملك الناس\nإله الناس\nمن شر الوسواس الخناس\nالذي يوسوس في صدور الناس\nمن الجنة والناس";
    private static final String SAYYID = "اللهم أنت ربي لا إله إلا أنت، خلقتني وأنا عبدك، وأنا على عهدك ووعدك ما استطعت، "
            + "أعوذ بك من شر ما صنعت، أبوء لك بنعمتك علي، وأبوء بذنبي فاغفر لي، فإنه لا يغفر الذنوب إلا أنت";
    private static final String BISM = "بسم الله الذي لا يضر مع اسمه شيء في الأرض ولا في السماء وهو السميع العليم";
    private static final String RADEET = "رضيت بالله ربا، وبالإسلام دينا، وبمحمد صلى الله عليه وسلم نبيا";
    private static final String HASBI = "حسبي الله لا إله إلا هو، عليه توكلت، وهو رب العرش العظيم";
    private static final String TAHLIL = "لا إله إلا الله وحده لا شريك له، له الملك وله الحمد، وهو على كل شيء قدير";

    static final Object[][][] ALL = {
        // Morning
        {
            {"آية الكرسي\n" + KURSI, 1}, {IKHLAS, 3}, {FALAQ, 3}, {NAS, 3},
            {"أصبحنا وأصبح الملك لله، والحمد لله، لا إله إلا الله وحده لا شريك له، له الملك وله الحمد، وهو على كل شيء قدير", 1},
            {"اللهم بك أصبحنا، وبك أمسينا، وبك نحيا، وبك نموت، وإليك النشور", 1},
            {SAYYID, 1}, {BISM, 3}, {RADEET, 3}, {HASBI, 7},
            {"سبحان الله وبحمده", 100}, {TAHLIL, 10}, {"أستغفر الله وأتوب إليه", 100},
            {"اللهم صل وسلم على نبينا محمد", 10},
        },
        // Evening
        {
            {"آية الكرسي\n" + KURSI, 1}, {IKHLAS, 3}, {FALAQ, 3}, {NAS, 3},
            {"أمسينا وأمسى الملك لله، والحمد لله، لا إله إلا الله وحده لا شريك له، له الملك وله الحمد، وهو على كل شيء قدير", 1},
            {"اللهم بك أمسينا، وبك أصبحنا، وبك نحيا، وبك نموت، وإليك المصير", 1},
            {SAYYID, 1}, {BISM, 3}, {RADEET, 3}, {HASBI, 7},
            {"أعوذ بكلمات الله التامات من شر ما خلق", 3},
            {"سبحان الله وبحمده", 100}, {"اللهم صل وسلم على نبينا محمد", 10},
        },
        // After prayer
        {
            {"أستغفر الله", 3},
            {"اللهم أنت السلام ومنك السلام، تباركت يا ذا الجلال والإكرام", 1},
            {TAHLIL, 1},
            {"سبحان الله", 33}, {"الحمد لله", 33}, {"الله أكبر", 33},
            {"آية الكرسي\n" + KURSI, 1},
        },
        // Before sleep
        {
            {"آية الكرسي\n" + KURSI, 1}, {IKHLAS, 3}, {FALAQ, 3}, {NAS, 3},
            {"باسمك اللهم أموت وأحيا", 1},
            {"سبحان الله", 33}, {"الحمد لله", 33}, {"الله أكبر", 34},
        },
    };

    /** Minutes after the adhan when the after-prayer adhkar are due. */
    static final int AFTER_MIN = 20;

    /**
     * Follows the day: after-prayer adhkar for half an hour once each prayer is prayed, morning from
     * Fajr until Asr, evening from Asr until late night, and before-sleep adhkar at night.
     */
    static int sectionNow(android.content.SharedPreferences prefs) {
        java.util.Calendar now = java.util.Calendar.getInstance();
        double h = now.get(java.util.Calendar.HOUR_OF_DAY) + now.get(java.util.Calendar.MINUTE) / 60.0;
        double[] t = Prayer.times(now, prefs);
        for (int i : new int[] {0, 2, 3, 4, 5}) {
            double from = t[i] + AFTER_MIN / 60.0;
            if (h >= from && h < from + 0.5) return 2;
        }
        if (h >= t[0] && h < t[3]) return 0;
        if (h >= t[3] && h < 22) return 1;
        return 3;
    }
}
