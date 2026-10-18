# Familien

Android-app til familiens madplan, opskrifter og indkøb. Skrevet i Kotlin med Jetpack Compose.

## Hvad appen kan (version 0.18)

- **Profiler:** hver telefon eller tablet vælger en profil. Forældreprofiler kræver forælder-PIN. Avataren øverst fører til familie og indstillinger.
- **Madplan:** en eller flere retter pr. dag (fx små retter eller en dessert), uge for uge fra søndag til lørdag. Vælg fra opskrifterne eller skriv en ret selv.
- **Forslag ud fra tilbud:** under Madplan foreslås aftensretter fra kartoteket med flest ingredienser på tilbud i de butikker, familien har valgt blandt Bilka, Rema 1000, Netto og Meny (via Tjek/eTilbudsavis, uofficielt API).
- **Opskrifter:** kartotek med ingredienser, fremgangsmåde og billede af retten, delt i aftensmad og desserter. Importér fra et link, del en side fra browseren til appen, eller skriv selv.
- **Indkøbsliste:** grupperet efter butiksafdeling. En vare kan flyttes til en anden afdeling, og valget huskes. Ens varer lægges sammen til én linje med summeret mængde og forkortede opskriftsnavne. Kun forældre kan rette listen.
- **Faste varer:** en redigerbar liste (morgenmad, madpakker), som lægges på ugens liste med ét tryk.
- **Kalender:** forældrene vælger én fælles Google-kalender. Appen viser dens aftaler sammen med aftensmaden og opretter nye aftaler direkte i den. Børnene behøver ingen Google-konto. Hver aftale kan angive, hvem den gælder for (gemt som en "Hvem:"-linje i aftalens beskrivelse). Faste aftaler (fx hver tirsdag) kan oprettes, og enkelte gange eller en hel periode kan aflyses. Aftaler kan vare flere dage, flyttes og slettes. Uge- og månedsvisning.
- **Forside:** hver profil vælger sine kort og deres rækkefølge: vejret i Hillerød morgen, middag og aften med regntidspunkt (Open-Meteo), S-tog linje A mellem Favrholm og Nørreport (Rejseplanen, kræver gratis nøgle), aftensmad, pligter, indkøbsliste og kalender.
- **Pligter:** faste pligter (barn og ugedage) og ad hoc-pligter med belønning. Barnet melder en pligt udført, en forælder bekræfter eller markerer den som ikke udført, og alle kan se hvem der gjorde hvad.

### Sådan virker kalenderen

Appen læser og skriver i Androids kalenderlager, som Google Kalender selv synkroniserer med Google. Der er derfor ingen Google-login eller Google Cloud-opsætning. Familiekalenderen skal være slået til i Google Kalender-appen på forældrenes telefoner, og en forælder skal have ret til at ændre i den for at oprette aftaler fra appen.

Enheder uden adgang til kalenderen (typisk børnenes) læser en kopi. Forældrenes telefoner lægger aftalerne fra en uge tilbage til 90 dage frem i familiens data, når appen åbnes og hvert kvarter i baggrunden. Børnene kan også oprette og ændre aftaler: ændringen gemmes i familiens data og udføres af en forælders telefon, når appen åbnes eller i baggrunden hvert kvarter.

### Profiler og sikkerhed

Begrænsningerne for børneprofiler (indkøbslisten, madplanen, bekræftelse af pligter) håndhæves i appen, ikke i databasen. Forælder-PIN gemmes som en hash i familiens data. Det er nok til at holde styr på hverdagen, men ikke en sikkerhedsgrænse over for et barn, der går målrettet efter at omgå den.

## Hent og installér appen

1. Åbn fanen **Actions** i repoet på GitHub, og vælg den seneste kørsel af "Byg app".
2. Hent filen `familieapp-debug` under **Artifacts**, og pak zip-filen ud.
3. Overfør `app-debug.apk` til telefonen, og åbn den. Android beder om lov til at installere fra en ukendt kilde.

Appen signeres med nøglen i `app/debug.keystore`, så en ny version kan installeres oven på den gamle.

## Byg selv

Åbn mappen i Android Studio, eller kør `./gradlew assembleDebug`. Kræver JDK 17.

## Slå deling til med Firebase

Uden Firebase gemmer appen kun på den enkelte telefon. Sådan deles data i familien:

1. Opret et projekt på <https://console.firebase.google.com>.
2. Tilføj en Android-app med pakkenavnet `dk.familieapp`.
3. Hent `google-services.json`, og læg den i mappen `app/` i repoet.
4. Slå **Authentication → Sign-in method → Anonymous** til.
5. Opret **Firestore Database** (produktionstilstand, fx `europe-west`).
6. Indsæt indholdet af `firestore.rules` under **Firestore → Rules**, og udgiv.

Når appen er bygget med filen, spørger den ved første start, om der skal oprettes en familie eller deltages med en familiekode. Koden findes under familien (tryk på din avatar øverst).

Data, der er gemt lokalt før Firebase blev slået til, flyttes ikke automatisk med.

## Opbygning

```
app/src/main/kotlin/dk/familieapp/
  data/   modeller, samlinger (Cols), FamilyStore (lokal og Firestore), opskriftsimport,
          sammenlægning af varer, pligtplan, kalender
  ui/     én fil pr. skærmområde, AppViewModel og AppRoot (navigation)
```

Et nyt modul tilføjes ved at lægge sine modeller i `Models.kt`, registrere en samling i `Cols`, lave en skærm i `ui/` og give den en rute i `AppRoot.kt`.

## Kendte begrænsninger

- Import virker kun på sider, der har opskriften som schema.org/Recipe (JSON-LD).
- Butiksafdelingen gættes ud fra varens navn og kan ikke rettes i appen endnu.
- Login er anonymt pr. telefon. Afinstalleres appen, deltager man igen med familiekoden.
