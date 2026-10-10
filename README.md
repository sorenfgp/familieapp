# Familien

Android-app til familiens madplan, opskrifter og indkøb. Skrevet i Kotlin med Jetpack Compose.

## Hvad appen kan (version 0.1)

- **Madplan:** en ret pr. dag, uge for uge. Vælg fra opskrifterne eller skriv en ret selv.
- **Opskrifter:** kartotek med ingredienser og fremgangsmåde. Importér fra et link, del en side fra browseren til appen, eller skriv selv.
- **Indkøbsliste:** grupperet efter butiksafdeling. Varer kan komme fra en opskrift, fra ugens madplan eller skrives ind.
- **Faste varer:** en redigerbar liste (morgenmad, madpakker), som lægges på ugens liste med ét tryk.
- **Kalender:** ikke med endnu. Google Kalender kommer i næste version.

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

Når appen er bygget med filen, spørger den ved første start, om der skal oprettes en familie eller deltages med en familiekode. Koden findes under **Mere**.

Data, der er gemt lokalt før Firebase blev slået til, flyttes ikke automatisk med.

## Opbygning

```
app/src/main/kotlin/dk/familieapp/
  data/   modeller, FamilyStore (lokal og Firestore), opskriftsimport, hjælpefunktioner
  ui/     én fil pr. skærmområde, AppViewModel og AppRoot (navigation)
```

Et nyt modul tilføjes ved at udvide `FamilyStore` med sine data, lave en skærm i `ui/` og give den en rute i `AppRoot.kt`.

## Kendte begrænsninger

- Import virker kun på sider, der har opskriften som schema.org/Recipe (JSON-LD).
- Butiksafdelingen gættes ud fra varens navn og kan ikke rettes i appen endnu.
- Login er anonymt pr. telefon. Afinstalleres appen, deltager man igen med familiekoden.
