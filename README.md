# Rapport : Analyse Statique avec LISA
- `IntervalSafeOverflowDomain`: Domaine non relationnel abstrait des intervalles prenant en compte les dépassements (difficulté : 4)
- `TwoVariablesInequalityDomain`: Domaine relationnel abstrait de deux variables par inéquation linéaire (difficulté : 4)
- `IntervalSafeOverflowTwoVariablesInequalityCartesianProduct` : Produit cartésien de ces 2 domaines abstraits

# **Domaine non rélationnel**

Cette prémière partie explique l'analyse des exemples de code fournis en relation avec les concepts d'opérations arithmétiques, de structures conditionnelles, de boucles, et leur gestion à l'aide du domaine abstrait `IntervalSafeOverflowDomain`. Ce domaine est conçu pour assurer une évaluation sécurisée et correcte des valeurs numériques dans un programme tout en évitant les dépassements d'entiers (overflows).

---

### Classe Principale : `IntervalSafeOverflowDomain.java`
### Input : `ìntervalleSafeOverflow.imp`

---
## 1. Opérations arithmétiques dans `basic()`
### Code étudié :
``` 
basic() {
    def i = 2147483647;
    def j = 20;
    def k = -j;
    def a = i + j;
    def s = i - j;
    def m = i * j;
    def d = i / j;
}
```
### Analyse et explication :
Ce code inclut des opérations arithmétiques de base :
- **Addition (`i + j`)** : Calcule la somme de deux valeurs.
- **Soustraction (`i - j`)** : Soustrait une valeur de l'autre.
- **Multiplication (`i * j`)** : Calcule le produit de deux valeurs.
- **Division entière (`i / j`)** : Divise les deux nombres en arrondissant le résultat vers zéro.
- **Négation (`-j`)** : Inverse le signe de la valeur.

### Gestion par `IntervalSafeOverflowDomain` :
Le domaine abstrait `IntervalSafeOverflowDomain` est capable d'effectuer ces calculs de manière sûre grâce à :
- Une représentation des intervalles numériques comme `[low..high]`.
- Une prise en charge explicite des dépassements d'entiers :
    - Si une addition, soustraction ou multiplication dépasse les limites de l'intervalle entier (`[-2^31, 2^31-1]`), l'intervalle est ajusté sans provoquer d'erreurs.

- Les fonctions comme `add`, `sub`, `mul` et `div` encapsulent ces comportements et gèrent les valeurs infinies et les cas limites (comme la division par zéro).
- Les 

# Résultat des analyses et des intervalles

Voici les intervalles correspondant aux variables utilisées, calculés après l'analyse abstraite effectuée via le domaine `IntervalSafeOverflowDomain` :

- **a** : `[-2147483629 .. -2147483629]`  
  Indique que la valeur de `a` est connue avec certitude et est exactement égale à `-2147483629`. Ceci est l'impacte directe de la façon dont nous avons géré l'overflow. Un dépassement de la borne supérieure positive nous renvoie dans la borne inférieure négative. 

- **d** : `[107374182 .. 107374182]`  
  Résultat de la division. La valeur est fixe et se situe exactement dans cet intervalle.

- **i** : `[+∞ .. +∞]`  
  La variable `i` est considérée comme ayant une valeur infinie positive.

- **j** : `[20 .. 20]`  
  La valeur de `j` est connue et fixe, égale à `20`.

- **k** : `[-20 .. -20]`  
  Résultat de la négation de `j`. La valeur est fixe et égale à `-20`.

- **m** : `[-20 .. -20]`  
  Multiplier deux valeurs aboutit ici à cet intervalle constant.

- **s** : `[2147483627 .. 2147483627]`  
  Résultat d'une soustraction, où la valeur est fixe et égale au nombre spécifié.

Ces intervalles montrent la précision de l'analyse abstraite, qui détermine les bornes exactes ou approximatives des valeurs des variables à chaque point du programme.

## 2. **Structure conditionnelle `if` dans `ifstatement()`**
### Code étudié :
``` 
ifstatement() {
    def i = 0;
    if(i > 0)
        i = 1;
    else
        def j = i * 2;
}
``` 

### Analyse et explication :
Ce code illustre une structure conditionnelle où la variable `i` est testée dans une condition :
- Si `i > 0`, sa valeur est modifiée (`i = 1`).
- Sinon, une nouvelle variable `j` est introduite en utilisant l'expression `i * 2`.

### Gestion par `IntervalSafeOverflowDomain` :
Le domaine abstrait gère ce type de logique via des **raffinements d'intervalles** :
- Lorsqu’une condition comme `i > 0` est rencontrée, le domaine ajuste dynamiquement les bornes de l'intervalle de `i` :
    - Dans la branche "true", l'intervalle devient `[1..+∞]`.
    - Dans la branche "false", l'intervalle devient `[-∞..0]`.

- Ces raffinements permettent de réduire les cas possibles pour les variables et d’optimiser l’analyse dans les deux branches. Il est géré dans la méthode `assumeBinary` et fait appel la méthode `lubAux` pour l'opérateur *LeastUpperBound*.

## 3. **Boucle `while` dans `whilestatement()`**
### Code étudié :
```
whilestatement() {
    def a = -2;
    def x = 0;
    while(a >= 1)
        x = x + 2;
}
```
### Analyse et explication :
Le bloc de code utilise une boucle `while` avec la condition `a >= 1`. Cependant, dans ce cas précis :
- La variable `a` est initialisée à `-2`, et la condition est immédiatement fausse.
- La boucle ne s'exécute jamais (`dead code`).

### Code étudié (widening) :
```
wideningwhilestatement() {
      def a = 0;
      def x = 1;
      while(a<=10) {
          x = x * 2;
          a = a + 1;
      }
  }
```
- À la différence de l'autre instruction de boucle `while`,
  celle-ci a eu recours à la méthode `widening`, à partir du 5-ème pour atteindre rapidement un point fixe et garantir la terminaison de la boucle.
-  **a** : `[-∞ .. 11]`
- **x** : `[2 .. +∞]`  


### Gestion par `IntervalSafeOverflowDomain` :
Le domaine abstrait gère les boucles en utilisant une **approximation via élargissement (`widening`)** :
- Si la condition incluait une mise à jour de la valeur de `a` (par exemple, `a -= 1`), le domaine utiliserait l'élargissement pour calculer une approximation sûre et terminer l'analyse.
- Cependant, comme la condition est statiquement fausse (`a = -2 < 1`), l'analyse conclut rapidement que la boucle est non atteignable, ce qui optimise le processus global.

## 4. **Fonctionnement de `IntervalSafeOverflowDomain`**
La classe `IntervalSafeOverflowDomain` implémente un domaine abstrait basé sur les **intervalles** `[low..high]` pour les variables. Voici ses fonctionnalités principales :
### Représentation :
- Chaque variable est associée à un intervalle, représentant toutes les valeurs possibles qu’elle peut prendre au moment de l’analyse.
- Le domaine prend en charge des bornes infinies positives (`+∞`) ou négatives (`-∞`) pour modéliser des intervalles non bornés.

### Opérations supportées :
1. **Addition, soustraction, multiplication et division** entre intervalles.
2. Gestion des **infinis** et des situations limites (par exemple, division par zéro).
3. Négation numérique, raffinements de bornes et gestion des dépassements d'entiers (overflow).

### Raffinement conditionnel :
Lors de tests basés sur des comparaisons (comme `x < 5` ou `y >= 0`), le domaine refactorise dynamiquement l'intervalle des variables en fonction de la condition testée.
### Boucles (`while`) :
Les boucles sont analysées à l'aide de techniques d'élargissement pour garantir que l'analyse se termine toujours.
- Cela permet de calculer des bornes approximatives même lorsque le nombre d'itérations est inconnu.

## 5. **Résumé**
Ce code prend en compte trois concepts fondamentaux dans l'analyse abstraite d'intervalles (domaine non rélationnel) :
1. **Opérations arithmétiques** : `IntervalSafeOverflowDomain` applique strictement des calculs sûrs pour éviter les dépassements (overflow) et gérer les bornes infinies.
2. **Structures conditionnelles (`if`)** : Le domaine raffine dynamiquement les intervalles dans chaque branche, optimisant la précision de l'analyse.
3. **Boucles (`while`)** : Grâce à l'élargissement, le domaine garantie une analyse toujours finie, même dans des boucles complexes.
4. **Possible amélioration** : Une amélioration de ce code consistera à traiter le `rand(a,b)`, `for`, `do ... while`. 

---

# **Domaine rélationnel**
La deuxième partie consiste à traite un domaine **TwoVariablesInequalityDomain** est un domaine abstrait relationnel dédié aux inéquations linéaires impliquant deux variables. Il est conçu pour analyser les relations linéaires (comme `ax + by ≤ c`) et simplifier ou étendre ces relations dans divers contextes programmatiques. Ce domaine peut être utilisé pour effectuer des analyses statiques sur des programmes afin de suivre des relations complexes entre variables avec précision.

---

## Classe Principale : `TwoVariablesInequalityDomain.java`
### Input : `TwoVariablesInequality.imp`

---
### Points Clés
1. **Constantes Domaines TOP et BOTTOM** :
  - `TOP` : Domaine contenant toutes les relations possibles.
  - `BOTTOM` : Domaine vide, représentant une contradiction.

2. **Attributs** :
  - `linearInequalities` : Représente l'ensemble des inéquations linéaires stockées sous forme simplifiée.
  - Domaines Top et Bottom sont indiqués par des flags (`isTopDomain` et `isBottomDomain`).

3. **Constructeurs** :
  - Un constructeur simplifié permet d'initialiser avec des inéquations spécifiques tout en les réduisant et en supprimant les chevauchements inutiles.

4. **Méthodes Principales** :
  - **`assign`** : Gestion des affectations de variables permettant des mises à jour de relations.
  - **`assume`** : Traitement des hypothèses exprimées sous forme d'inéquations à deux variables.
  - **`lub`/`glb`** : Calcul du plus petit majorant (`lub`) et plus grand minorant (`glb`) entre deux domaines.
  - **`processAndSimplifyLinearInequalities`** : Simplification des inéquations linéaires pour éviter la redondance et générer des relations dérivées par transitivité.

---

## Représentation de l'Inéquation Linéaire : `LinearInequality`

Chaque inéquation suit la forme générale :  
**`a*x + b*y ≤ c`**

### Attributs :
- **Coefficients des variables (`variableCoefficientsMap`)** : Une map associant chaque variable (de type `Identifier`) à son coefficient.
- **Constante droite (`c`)** : Valeur qui borne l'inéquation.
- **Type de contrainte** :
  - Par défaut : `≤`
  - Peut également être `<` (strict).

### Simplification et Vérification :
1. **Détection de relations triviales** :
  - Exemple : `0*x + 0*y ≤ c` où `c > 0` est considéré comme futile par `isTrivialInequality`.
2. **Vérification des inéquations mono-variables** :
  - Les inéquations ne traitant qu'une seule variable sont généralement ignorées ou simplifiées via la méthode `isSingleVariableInequality`.
3. **Supprime les inéquations rédondantes** :
  - Si l'environnement contient 2 ou plusieurs inéquations identiques, on ne retient qu'une seule. 
4.**Transitivité** :
  - Les nouvelles relations sont générées en combinant les inéquations existantes (Ex. transitivité entre `x ≤ 5` et `x + y ≤ 7`).
Le **4** et **5** sont combiné dans l'implémentation de `processAndSimplifyLinearInequalities`

---

## Fonctionnalités Clés

### Manipulation de Variables :
1. **`assign`** :
  - Permet de traiter les affectations dans le programme.
  - Exemple : Si `u = z - 2`, alors une nouvelle inégalité `u - z <= -2` est ajoutée au domaine.

2. **Mise à jour avec des Comparaisons** :
  - Traite des expressions binaires de type `BinaryExpression` où l'opérateur est une comparaison (`ComparisonLe`).
  - Simplifie des relations comme `(2*x + 5*y) <= 3`.

### Gestion des Inéquations :
1. **Combinaisons et Réductions** :
  - Génération d'un nouvel ensemble d'inéquations en fusionnant ou simplifiant par transitivité les existantes.
  - Exemple : `x + y ≤ 5` combiné avec `x + y ≤ 3` pourrait produire `x + y ≤ 3`.

2. **Support des Opérations Binaires** :
  - Support pour `+`, `-`, et parfois pour `*` si l'un des termes est une constante. Exemple : `2*x + 5*y ≤ 10`.

---

## Points Importants du Code

1. **Gestion des Identifiants** :
  - Les identifiants spécifiques au tas (`heap`, `this`, etc.) sont ignorés dans les mises à jour.

2. **Gestion des Assumptions et Simulations Étape par Étape** :
  - Les hypothèses non linéaires ou hors domaine ne modifient pas l'état actuel.

3. **Représentation en String** :
  - Les inéquations sont affichées dans une syntaxe proche de la mathématique classique, facilitant la lecture des sorties du domaine.

4. **Support Étendu pour le Domaine Relationnel** :
  - Les méthodes génériques comme `lub`, `glb`, et `satisfies` permettent de manipuler efficacement les relations au sein du domaine lors des analyses programmatiques.

---

## Cas d'Application

### Exemples :
#### 1. Expression Simple :
```java
x = z + 5;
y = x + 3;
```
Cela se traduit par l'ajout des inéquations suivantes :
- `x - z <= 5`
- `y - x <= 3`

#### 2. Hypothèses Conditionnelles :
```java
if ((1*x + 5*y) <= 4) { ... }
```
L'analyse gère ce type d'expression en stockant la contrainte `(1*x + 5*y) <= 4` comme une inéquation linéaire.

#### 3. Simplification via Transitivité :
Given :
- Relation 1 : `x + y ≤ 3`
- Relation 2 : `y - z ≤ 5`

Les deux relations produisent transitivement une autre inéquation : `x - z ≤ 8`.

---

## **Résumé**
Le domaine `TwoVariablesInequalityDomain` fournit une base puissante pour analyser des systèmes simples d'inéquations linéaires avec deux variables. Il s’intègre bien dans des analyses plus globales grâce à ses capacités de simplification et de fermeture transitives. 
Il est quand même encore possible d'élargir davantage les instructions couvertes par cette implementation (ex: en tenant compte toutes les autres inégalités)

---

# Produit Cartésien de Domaines

 traite de l'analyse basée sur **le produit cartésien** entre deux domaines, :

---

### Classe Principale : `IntervalSafeOverflowDomain.java`
### Input : `ProduitCartesian.imp`

---
```
basic_linear(){
    def x = -1;
    def z = 2;
    def u = 6;
    u = z - 2;
    def y = x + 5;
}
```

## Contexte : Le Produit Cartésien
L'approche implémentée repose sur la combinaison de deux domaines principaux :
1. **Domaine des Inéquations Linéaires à Deux Variables** : Suit et interprète des relations telles que `ax + by ≤ c`.
2. **Domaine des Intervalles** : Fournit des informations numériques précises sur les bornes des variables pour éviter les débordements, grâce au domaine `IntervalSafeOverflowDomain`.

Le **produit cartésien** combine ces deux domaines pour analyser des relations complexes tout en validant ou invalidant les contraintes en projetant des intervalles dans les inéquations.

---

## Fonctionnalité Clé : Réduction via le Produit Cartésien
La réduction consiste à croiser les informations des deux domaines pour valider ou invalider les relations définies entre les variables. Voici un résumé des étapes principales :

1. ***Extraction des contraintes relationnelles*** :
  - Les inégalités du domaine linéaire sont analysées.

2. ***Projection des intervalles*** :
  - Les bornes des variables issues des intervalles sont multipliées par leurs coefficients dans chaque inégalité.
  - Les bornes minimales (`min`) et maximales (`max`) pour chaque inégalité sont calculées en combinant les projections.

3. ***Vérification des contradictions*** :
  - Une inégalité est validée si ses valeurs projetées respectent la contrainte (par exemple, `max ≤ c`, où `c` est la constante de l’inégalité).
  - En cas de contradiction, les variables impliquées sont mises sur **TOP**.

---

## Application au Code fourni

Examinons le code `basic_linear()` avec cette approche.

### Étape 1 : Compilation des Contraintes et Intervalles
Voici les informations initiales extraites des deux domaines :

| **Variable** | **Expression**          | **Intervalle (Bornes)**       | **Inéquation (Relations issues du domaine)** |
|--------------|--------------------------|-------------------------------|-------------------------------------------|
| `x`          | `-1`                    | `[-1, -1]`                   | TOP                                       |
| `z`          | `2`                     | `[2, 2]`                     | TOP                                       |
| `u`          | `6`                     | `[6, 6]`                     | TOP                                       |
| `u`          | `z - 2`                 | `2 - 2 = 0`                  | `u - z ≤ -2`                              |
| `y`          | `x + 5`                 | `(-1) + 5 = [4, 4]`          | `y - x ≤ 5`                               |

Les environnements finaux seront
`{ y - x <= 5 , u - z <= -2 }`
et
`u`: `[0 .. 0]`,
`x`: `[-1 .. -1]`,
`y`: `[4 .. 4]`,
`z`: `[2 .. 2]` 

---

### Étape 2 : Vérification et Réduction

#### **Inégalité 1 : `u - z ≤ -2`**
- **Calcul Projeté :**
  - Intervalle de `u` : `[6, 6]`
  - Intervalle de `z` : `[2, 2]`
  - Coefficient de `u` : `1`
  - Coefficient de `z` : `-1`
  - Projections :
    ```
    u : [+1 * 6, +1 * 6] = [6, 6]
    z : [-1 * 2, -1 * 2] = [-2, -2]
    min = 6 + (-2) = 4
    max = 6 + (-2) = 4
    ```
- **Résultat :**
  - La borne maximale projetée (`4`) ne respecte pas la contrainte attendue (`max ≤ -2`).
- **Action attendue :**
  - Une contradiction est détectée pour `u - z ≤ -2` : les variables `u` et `z` doivent être mises sur **TOP** dans le domaine des intervalles.

#### **Inégalité 2 : `y - x ≤ 5`**
- **Calcul Projeté :**
  - Intervalle de `y` : `[4, 4]`
  - Intervalle de `x` : `[-1, -1]`
  - Coefficient de `y` : `1`
  - Coefficient de `x` : `-1`
  - Projections :
    ```
    y : [+1 * 4, +1 * 4] = [4, 4]
    x : [-1 * -1, -1 * -1] = [1, 1]
    min = 4 + 1 = 5
    max = 4 + 1 = 5
    ```
- **Résultat :**
  - La borne maximale projetée (`5`) respecte bien la contrainte attendue (`max ≤ 5`).
  - L'inégalité est valide.

---

### Étape 3 : Résultat attendu après Réduction
Après la réduction, les domaines devraient être transformés comme suit :

#### **Domaine Relationnel (Inéquations)** :
- `u - z ≤ -2` : Contradiction détectée, remplacée par la mise des variables sur **TOP**.
- `y - x ≤ 5` : Validée, restée inchangée.

#### **Domaine Intervallaire (Bornes)** :
- `x ∈ [-1, -1]`
- `z ∈ TOP` *(en raison de la contradiction)*
- `u ∈ TOP` *(en raison de la contradiction)*
- `y ∈ [4, 4]`

---

## Limitation du Code Actuel

### Comportement Observé dans le Code Actuel
Dans le code, la logique de projection et de vérification des inégalités (`reduce`) est correctement mise en œuvre pour identifier les contradictions. Cependant, **le code ne met pas correctement à jour les variables avec l'état abstrait TOP (`newRight.putState`) lorsqu'une contradiction est détectée**.

### Ajustement Nécessaire
Pour que le processus de réduction fonctionne comme prévu :
1. Lorsqu'une contradiction est identifiée, il faut assurer que toutes les variables impliquées dans l'inégalité contradictoire soient mises sur TOP, comme indiqué dans cette section :
   ```java
   if (!isValid) {
       System.out.println("Contradiction detected for inequality: " + linearInequality);

       // Si l'inégalité est invalide, mettre toutes ses variables sur TOP
       for (Identifier variable : variableCoefficientsMap.keySet()) {
           System.out.println("Setting variable " + variable + " to TOP due to invalid inequality.");
           newRight = newRight.putState(variable, new IntervalSafeOverflowDomain().top());
       }
   }
   ```
2. S'assurer que `newRight` soit effectivement mis à jour dans l'objet final créé à la fin de la méthode `reduce`.

L'absence de cette mise à jour provoque une incohérence entre l'analyse conceptuelle et les résultats réellement retournés par l'analyse.

---

## **Résumé**
Le **produit cartésien** proposé combine efficacement les domaines relationnel et intervallaire pour :
1. Détecter des contradictions en projetant les intervalles dans les relations linéaires.
2. Gérer les imprécisions en utilisant l'état abstrait TOP.

### Résultat Attendu
Pour le code `basic_linear()` :
- `x ∈ [-1, -1]`
- `z ∈ TOP` *(en raison de la contradiction dans `u - z ≤ -2`)*
- `u ∈ TOP` *(en raison de la contradiction dans `u - z ≤ -2`)*
- `y ∈ [4, 4]`

### Modification Nécessaire
L'algorithme **doit être ajusté pour profiter de la communication des 2 domaines et garantir la mise à jour des variables en TOP** lors d'une contradiction, comme décrit dans la section des ajustements nécessaires.

Une fois cette correction apportée, l'analyse produira les résultats attendus et permettra une utilisation correcte dans des scénarios plus complexes.