<p align='center'>
<a href='/docs/developer-guide/core/users/2fa.md'>« Previous section</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<a href='/docs/developer-guide/core/monitoring/introduction.md'>Next section »</a>
</p>


[UCloud Developer Guide](/docs/developer-guide/README.md) / [Core](/docs/developer-guide/core/README.md) / [Users](/docs/developer-guide/core/users/README.md) / Avatars
# Avatars

[![API: Internal/Stable](https://img.shields.io/static/v1?label=API&message=Internal/Stable&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)

_Provides user avatars. User avatars are provided by the https://avataaars.com/ library._

## Rationale

All users have an avatar associated with them. A default avatar will be
returned if one is not found in the database. As a result, this service does
not need to listen for user created events.

The avatar are mainly used as a way for users to easier distinguish between different users when sharing or
working in projects.

 ---
    
__⚠️ WARNING:__ The API listed on this page will likely change to conform with our
[API conventions](/docs/developer-guide/core/api-conventions.md). Be careful when building integrations. The following
changes are expected:

- RPC names will change to conform with the conventions
- RPC request and response types will change to conform with the conventions
- RPCs which return a page will be collapsed into a single `browse` endpoint
- Some property names will change to be consistent with [`Resource`](/docs/reference/dk.sdu.cloud.provider.api.Resource.md)s

---

## Table of Contents
<details>
<summary>
<a href='#remote-procedure-calls'>1. Remote Procedure Calls</a>
</summary>

<table><thead><tr>
<th>Name</th>
<th>Description</th>
</tr></thread>
<tbody>
<tr>
<td><a href='#findavatar'><code>findAvatar</code></a></td>
<td>Request the avatar of the current user.</td>
</tr>
<tr>
<td><a href='#findbulk'><code>findBulk</code></a></td>
<td>Request the avatars of one or more users by username.</td>
</tr>
<tr>
<td><a href='#update'><code>update</code></a></td>
<td>Update the avatar of the current user.</td>
</tr>
</tbody></table>


</details>

<details>
<summary>
<a href='#data-models'>2. Data Models</a>
</summary>

<table><thead><tr>
<th>Name</th>
<th>Description</th>
</tr></thread>
<tbody>
<tr>
<td><a href='#avatar'><code>Avatar</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#clothes'><code>Clothes</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#clothesgraphic'><code>ClothesGraphic</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#colorfabric'><code>ColorFabric</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#eyebrows'><code>Eyebrows</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#eyes'><code>Eyes</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#facialhair'><code>FacialHair</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#facialhaircolor'><code>FacialHairColor</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#haircolor'><code>HairColor</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#hatcolor'><code>HatColor</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#mouthtypes'><code>MouthTypes</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#skincolors'><code>SkinColors</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#top'><code>Top</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#topaccessory'><code>TopAccessory</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#findbulkrequest'><code>FindBulkRequest</code></a></td>
<td><i>No description</i></td>
</tr>
<tr>
<td><a href='#findbulkresponse'><code>FindBulkResponse</code></a></td>
<td><i>No description</i></td>
</tr>
</tbody></table>


</details>


## Remote Procedure Calls

### `findAvatar`

[![API: Internal/Stable](https://img.shields.io/static/v1?label=API&message=Internal/Stable&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Request the avatar of the current user._

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='#avatar'>Avatar</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `findBulk`

[![API: Internal/Stable](https://img.shields.io/static/v1?label=API&message=Internal/Stable&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Authenticated](https://img.shields.io/static/v1?label=Auth&message=Authenticated&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Request the avatars of one or more users by username._

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#findbulkrequest'>FindBulkRequest</a></code>|<code><a href='#findbulkresponse'>FindBulkResponse</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|



### `update`

[![API: Internal/Stable](https://img.shields.io/static/v1?label=API&message=Internal/Stable&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)
[![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)](/docs/developer-guide/core/types.md#role)


_Update the avatar of the current user._

| Request | Response | Error |
|---------|----------|-------|
|<code><a href='#avatar'>Avatar</a></code>|<code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-unit/'>Unit</a></code>|<code><a href='/docs/reference/dk.sdu.cloud.CommonErrorMessage.md'>CommonErrorMessage</a></code>|




## Data Models

### `Avatar`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class Avatar(
    val top: Top,
    val topAccessory: TopAccessory,
    val hairColor: HairColor,
    val facialHair: FacialHair,
    val facialHairColor: FacialHairColor,
    val clothes: Clothes,
    val colorFabric: ColorFabric,
    val eyes: Eyes,
    val eyebrows: Eyebrows,
    val mouthTypes: MouthTypes,
    val skinColors: SkinColors,
    val clothesGraphic: ClothesGraphic,
    val hatColor: HatColor,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>top</code>: <code><code><a href='#top'>Top</a></code></code>
</summary>





</details>

<details>
<summary>
<code>topAccessory</code>: <code><code><a href='#topaccessory'>TopAccessory</a></code></code>
</summary>





</details>

<details>
<summary>
<code>hairColor</code>: <code><code><a href='#haircolor'>HairColor</a></code></code>
</summary>





</details>

<details>
<summary>
<code>facialHair</code>: <code><code><a href='#facialhair'>FacialHair</a></code></code>
</summary>





</details>

<details>
<summary>
<code>facialHairColor</code>: <code><code><a href='#facialhaircolor'>FacialHairColor</a></code></code>
</summary>





</details>

<details>
<summary>
<code>clothes</code>: <code><code><a href='#clothes'>Clothes</a></code></code>
</summary>





</details>

<details>
<summary>
<code>colorFabric</code>: <code><code><a href='#colorfabric'>ColorFabric</a></code></code>
</summary>





</details>

<details>
<summary>
<code>eyes</code>: <code><code><a href='#eyes'>Eyes</a></code></code>
</summary>





</details>

<details>
<summary>
<code>eyebrows</code>: <code><code><a href='#eyebrows'>Eyebrows</a></code></code>
</summary>





</details>

<details>
<summary>
<code>mouthTypes</code>: <code><code><a href='#mouthtypes'>MouthTypes</a></code></code>
</summary>





</details>

<details>
<summary>
<code>skinColors</code>: <code><code><a href='#skincolors'>SkinColors</a></code></code>
</summary>





</details>

<details>
<summary>
<code>clothesGraphic</code>: <code><code><a href='#clothesgraphic'>ClothesGraphic</a></code></code>
</summary>





</details>

<details>
<summary>
<code>hatColor</code>: <code><code><a href='#hatcolor'>HatColor</a></code></code>
</summary>





</details>



</details>



---

### `Clothes`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
enum class Clothes {
    BLAZER_SHIRT,
    BLAZER_SWEATER,
    COLLAR_SWEATER,
    GRAPHIC_SHIRT,
    HOODIE,
    OVERALL,
    SHIRT_CREW_NECK,
    SHIRT_SCOOP_NECK,
    SHIRT_V_NECK,
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>BLAZER_SHIRT</code>
</summary>





</details>

<details>
<summary>
<code>BLAZER_SWEATER</code>
</summary>





</details>

<details>
<summary>
<code>COLLAR_SWEATER</code>
</summary>





</details>

<details>
<summary>
<code>GRAPHIC_SHIRT</code>
</summary>





</details>

<details>
<summary>
<code>HOODIE</code>
</summary>





</details>

<details>
<summary>
<code>OVERALL</code>
</summary>





</details>

<details>
<summary>
<code>SHIRT_CREW_NECK</code>
</summary>





</details>

<details>
<summary>
<code>SHIRT_SCOOP_NECK</code>
</summary>





</details>

<details>
<summary>
<code>SHIRT_V_NECK</code>
</summary>





</details>



</details>



---

### `ClothesGraphic`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
enum class ClothesGraphic {
    BAT,
    CUMBIA,
    DEER,
    DIAMOND,
    HOLA,
    PIZZA,
    RESIST,
    SELENA,
    BEAR,
    SKULL_OUTLINE,
    SKULL,
    ESPIE,
    ESCIENCELOGO,
    TEETH,
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>BAT</code>
</summary>





</details>

<details>
<summary>
<code>CUMBIA</code>
</summary>





</details>

<details>
<summary>
<code>DEER</code>
</summary>





</details>

<details>
<summary>
<code>DIAMOND</code>
</summary>





</details>

<details>
<summary>
<code>HOLA</code>
</summary>





</details>

<details>
<summary>
<code>PIZZA</code>
</summary>





</details>

<details>
<summary>
<code>RESIST</code>
</summary>





</details>

<details>
<summary>
<code>SELENA</code>
</summary>





</details>

<details>
<summary>
<code>BEAR</code>
</summary>





</details>

<details>
<summary>
<code>SKULL_OUTLINE</code>
</summary>





</details>

<details>
<summary>
<code>SKULL</code>
</summary>





</details>

<details>
<summary>
<code>ESPIE</code>
</summary>





</details>

<details>
<summary>
<code>ESCIENCELOGO</code>
</summary>





</details>

<details>
<summary>
<code>TEETH</code>
</summary>





</details>



</details>



---

### `ColorFabric`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
enum class ColorFabric {
    BLACK,
    BLUE01,
    BLUE02,
    BLUE03,
    GRAY01,
    GRAY02,
    HEATHER,
    PASTEL_BLUE,
    PASTEL_GREEN,
    PASTEL_ORANGE,
    PASTEL_RED,
    PASTEL_YELLOW,
    PINK,
    RED,
    WHITE,
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>BLACK</code>
</summary>





</details>

<details>
<summary>
<code>BLUE01</code>
</summary>





</details>

<details>
<summary>
<code>BLUE02</code>
</summary>





</details>

<details>
<summary>
<code>BLUE03</code>
</summary>





</details>

<details>
<summary>
<code>GRAY01</code>
</summary>





</details>

<details>
<summary>
<code>GRAY02</code>
</summary>





</details>

<details>
<summary>
<code>HEATHER</code>
</summary>





</details>

<details>
<summary>
<code>PASTEL_BLUE</code>
</summary>





</details>

<details>
<summary>
<code>PASTEL_GREEN</code>
</summary>





</details>

<details>
<summary>
<code>PASTEL_ORANGE</code>
</summary>





</details>

<details>
<summary>
<code>PASTEL_RED</code>
</summary>





</details>

<details>
<summary>
<code>PASTEL_YELLOW</code>
</summary>





</details>

<details>
<summary>
<code>PINK</code>
</summary>





</details>

<details>
<summary>
<code>RED</code>
</summary>





</details>

<details>
<summary>
<code>WHITE</code>
</summary>





</details>



</details>



---

### `Eyebrows`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
enum class Eyebrows {
    ANGRY,
    ANGRY_NATURAL,
    DEFAULT,
    DEFAULT_NATURAL,
    FLAT_NATURAL,
    FROWN_NATURAL,
    RAISED_EXCITED,
    RAISED_EXCITED_NATURAL,
    SAD_CONCERNED,
    SAD_CONCERNED_NATURAL,
    UNIBROW_NATURAL,
    UP_DOWN,
    UP_DOWN_NATURAL,
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>ANGRY</code>
</summary>





</details>

<details>
<summary>
<code>ANGRY_NATURAL</code>
</summary>





</details>

<details>
<summary>
<code>DEFAULT</code>
</summary>





</details>

<details>
<summary>
<code>DEFAULT_NATURAL</code>
</summary>





</details>

<details>
<summary>
<code>FLAT_NATURAL</code>
</summary>





</details>

<details>
<summary>
<code>FROWN_NATURAL</code>
</summary>





</details>

<details>
<summary>
<code>RAISED_EXCITED</code>
</summary>





</details>

<details>
<summary>
<code>RAISED_EXCITED_NATURAL</code>
</summary>





</details>

<details>
<summary>
<code>SAD_CONCERNED</code>
</summary>





</details>

<details>
<summary>
<code>SAD_CONCERNED_NATURAL</code>
</summary>





</details>

<details>
<summary>
<code>UNIBROW_NATURAL</code>
</summary>





</details>

<details>
<summary>
<code>UP_DOWN</code>
</summary>





</details>

<details>
<summary>
<code>UP_DOWN_NATURAL</code>
</summary>





</details>



</details>



---

### `Eyes`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
enum class Eyes {
    CLOSE,
    CRY,
    DEFAULT,
    DIZZY,
    EYE_ROLL,
    HAPPY,
    HEARTS,
    SIDE,
    SQUINT,
    SURPRISED,
    WINK,
    WINK_WACKY,
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>CLOSE</code>
</summary>





</details>

<details>
<summary>
<code>CRY</code>
</summary>





</details>

<details>
<summary>
<code>DEFAULT</code>
</summary>





</details>

<details>
<summary>
<code>DIZZY</code>
</summary>





</details>

<details>
<summary>
<code>EYE_ROLL</code>
</summary>





</details>

<details>
<summary>
<code>HAPPY</code>
</summary>





</details>

<details>
<summary>
<code>HEARTS</code>
</summary>





</details>

<details>
<summary>
<code>SIDE</code>
</summary>





</details>

<details>
<summary>
<code>SQUINT</code>
</summary>





</details>

<details>
<summary>
<code>SURPRISED</code>
</summary>





</details>

<details>
<summary>
<code>WINK</code>
</summary>





</details>

<details>
<summary>
<code>WINK_WACKY</code>
</summary>





</details>



</details>



---

### `FacialHair`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
enum class FacialHair {
    BLANK,
    BEARD_MEDIUM,
    BEARD_LIGHT,
    BEARD_MAJESTIC,
    MOUSTACHE_FANCY,
    MOUSTACHE_MAGNUM,
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>BLANK</code>
</summary>





</details>

<details>
<summary>
<code>BEARD_MEDIUM</code>
</summary>





</details>

<details>
<summary>
<code>BEARD_LIGHT</code>
</summary>





</details>

<details>
<summary>
<code>BEARD_MAJESTIC</code>
</summary>





</details>

<details>
<summary>
<code>MOUSTACHE_FANCY</code>
</summary>





</details>

<details>
<summary>
<code>MOUSTACHE_MAGNUM</code>
</summary>





</details>



</details>



---

### `FacialHairColor`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
enum class FacialHairColor {
    AUBURN,
    BLACK,
    BLONDE,
    BLONDE_GOLDEN,
    BROWN,
    BROWN_DARK,
    PLATINUM,
    RED,
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>AUBURN</code>
</summary>





</details>

<details>
<summary>
<code>BLACK</code>
</summary>





</details>

<details>
<summary>
<code>BLONDE</code>
</summary>





</details>

<details>
<summary>
<code>BLONDE_GOLDEN</code>
</summary>





</details>

<details>
<summary>
<code>BROWN</code>
</summary>





</details>

<details>
<summary>
<code>BROWN_DARK</code>
</summary>





</details>

<details>
<summary>
<code>PLATINUM</code>
</summary>





</details>

<details>
<summary>
<code>RED</code>
</summary>





</details>



</details>



---

### `HairColor`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
enum class HairColor {
    AUBURN,
    BLACK,
    BLONDE,
    BLONDE_GOLDEN,
    BROWN,
    BROWN_DARK,
    PASTEL_PINK,
    PLATINUM,
    RED,
    SILVER_GRAY,
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>AUBURN</code>
</summary>





</details>

<details>
<summary>
<code>BLACK</code>
</summary>





</details>

<details>
<summary>
<code>BLONDE</code>
</summary>





</details>

<details>
<summary>
<code>BLONDE_GOLDEN</code>
</summary>





</details>

<details>
<summary>
<code>BROWN</code>
</summary>





</details>

<details>
<summary>
<code>BROWN_DARK</code>
</summary>





</details>

<details>
<summary>
<code>PASTEL_PINK</code>
</summary>





</details>

<details>
<summary>
<code>PLATINUM</code>
</summary>





</details>

<details>
<summary>
<code>RED</code>
</summary>





</details>

<details>
<summary>
<code>SILVER_GRAY</code>
</summary>





</details>



</details>



---

### `HatColor`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
enum class HatColor {
    BLACK,
    BLUE01,
    BLUE02,
    BLUE03,
    GRAY01,
    GRAY02,
    HEATHER,
    PASTELBLUE,
    PASTELGREEN,
    PASTELORANGE,
    PASTELRED,
    PASTELYELLOW,
    PINK,
    RED,
    WHITE,
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>BLACK</code>
</summary>





</details>

<details>
<summary>
<code>BLUE01</code>
</summary>





</details>

<details>
<summary>
<code>BLUE02</code>
</summary>





</details>

<details>
<summary>
<code>BLUE03</code>
</summary>





</details>

<details>
<summary>
<code>GRAY01</code>
</summary>





</details>

<details>
<summary>
<code>GRAY02</code>
</summary>





</details>

<details>
<summary>
<code>HEATHER</code>
</summary>





</details>

<details>
<summary>
<code>PASTELBLUE</code>
</summary>





</details>

<details>
<summary>
<code>PASTELGREEN</code>
</summary>





</details>

<details>
<summary>
<code>PASTELORANGE</code>
</summary>





</details>

<details>
<summary>
<code>PASTELRED</code>
</summary>





</details>

<details>
<summary>
<code>PASTELYELLOW</code>
</summary>





</details>

<details>
<summary>
<code>PINK</code>
</summary>





</details>

<details>
<summary>
<code>RED</code>
</summary>





</details>

<details>
<summary>
<code>WHITE</code>
</summary>





</details>



</details>



---

### `MouthTypes`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
enum class MouthTypes {
    CONCERNED,
    DEFAULT,
    DISBELIEF,
    EATING,
    GRIMACE,
    SAD,
    SCREAM_OPEN,
    SERIOUS,
    SMILE,
    TONGUE,
    TWINKLE,
    VOMIT,
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>CONCERNED</code>
</summary>





</details>

<details>
<summary>
<code>DEFAULT</code>
</summary>





</details>

<details>
<summary>
<code>DISBELIEF</code>
</summary>





</details>

<details>
<summary>
<code>EATING</code>
</summary>





</details>

<details>
<summary>
<code>GRIMACE</code>
</summary>





</details>

<details>
<summary>
<code>SAD</code>
</summary>





</details>

<details>
<summary>
<code>SCREAM_OPEN</code>
</summary>





</details>

<details>
<summary>
<code>SERIOUS</code>
</summary>





</details>

<details>
<summary>
<code>SMILE</code>
</summary>





</details>

<details>
<summary>
<code>TONGUE</code>
</summary>





</details>

<details>
<summary>
<code>TWINKLE</code>
</summary>





</details>

<details>
<summary>
<code>VOMIT</code>
</summary>





</details>



</details>



---

### `SkinColors`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
enum class SkinColors {
    TANNED,
    YELLOW,
    PALE,
    LIGHT,
    BROWN,
    DARK_BROWN,
    BLACK,
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>TANNED</code>
</summary>





</details>

<details>
<summary>
<code>YELLOW</code>
</summary>





</details>

<details>
<summary>
<code>PALE</code>
</summary>





</details>

<details>
<summary>
<code>LIGHT</code>
</summary>





</details>

<details>
<summary>
<code>BROWN</code>
</summary>





</details>

<details>
<summary>
<code>DARK_BROWN</code>
</summary>





</details>

<details>
<summary>
<code>BLACK</code>
</summary>





</details>



</details>



---

### `Top`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
enum class Top {
    NO_HAIR,
    EYEPATCH,
    HAT,
    HIJAB,
    TURBAN,
    WINTER_HAT1,
    WINTER_HAT2,
    WINTER_HAT3,
    WINTER_HAT4,
    LONG_HAIR_BIG_HAIR,
    LONG_HAIR_BOB,
    LONG_HAIR_BUN,
    LONG_HAIR_CURLY,
    LONG_HAIR_CURVY,
    LONG_HAIR_DREADS,
    LONG_HAIR_FRIDA,
    LONG_HAIR_FRO,
    LONG_HAIR_FRO_BAND,
    LONG_HAIR_NOT_TOO_LONG,
    LONG_HAIR_SHAVED_SIDES,
    LONG_HAIR_MIA_WALLACE,
    LONG_HAIR_STRAIGHT,
    LONG_HAIR_STRAIGHT2,
    LONG_HAIR_STRAIGHT_STRAND,
    SHORT_HAIR_DREADS01,
    SHORT_HAIR_DREADS02,
    SHORT_HAIR_FRIZZLE,
    SHORT_HAIR_SHAGGY_MULLET,
    SHORT_HAIR_SHORT_CURLY,
    SHORT_HAIR_SHORT_FLAT,
    SHORT_HAIR_SHORT_ROUND,
    SHORT_HAIR_SHORT_WAVED,
    SHORT_HAIR_SIDES,
    SHORT_HAIR_THE_CAESAR,
    SHORT_HAIR_THE_CAESAR_SIDE_PART,
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>NO_HAIR</code>
</summary>





</details>

<details>
<summary>
<code>EYEPATCH</code>
</summary>





</details>

<details>
<summary>
<code>HAT</code>
</summary>





</details>

<details>
<summary>
<code>HIJAB</code>
</summary>





</details>

<details>
<summary>
<code>TURBAN</code>
</summary>





</details>

<details>
<summary>
<code>WINTER_HAT1</code>
</summary>





</details>

<details>
<summary>
<code>WINTER_HAT2</code>
</summary>





</details>

<details>
<summary>
<code>WINTER_HAT3</code>
</summary>





</details>

<details>
<summary>
<code>WINTER_HAT4</code>
</summary>





</details>

<details>
<summary>
<code>LONG_HAIR_BIG_HAIR</code>
</summary>





</details>

<details>
<summary>
<code>LONG_HAIR_BOB</code>
</summary>





</details>

<details>
<summary>
<code>LONG_HAIR_BUN</code>
</summary>





</details>

<details>
<summary>
<code>LONG_HAIR_CURLY</code>
</summary>





</details>

<details>
<summary>
<code>LONG_HAIR_CURVY</code>
</summary>





</details>

<details>
<summary>
<code>LONG_HAIR_DREADS</code>
</summary>





</details>

<details>
<summary>
<code>LONG_HAIR_FRIDA</code>
</summary>





</details>

<details>
<summary>
<code>LONG_HAIR_FRO</code>
</summary>





</details>

<details>
<summary>
<code>LONG_HAIR_FRO_BAND</code>
</summary>





</details>

<details>
<summary>
<code>LONG_HAIR_NOT_TOO_LONG</code>
</summary>





</details>

<details>
<summary>
<code>LONG_HAIR_SHAVED_SIDES</code>
</summary>





</details>

<details>
<summary>
<code>LONG_HAIR_MIA_WALLACE</code>
</summary>





</details>

<details>
<summary>
<code>LONG_HAIR_STRAIGHT</code>
</summary>





</details>

<details>
<summary>
<code>LONG_HAIR_STRAIGHT2</code>
</summary>





</details>

<details>
<summary>
<code>LONG_HAIR_STRAIGHT_STRAND</code>
</summary>





</details>

<details>
<summary>
<code>SHORT_HAIR_DREADS01</code>
</summary>





</details>

<details>
<summary>
<code>SHORT_HAIR_DREADS02</code>
</summary>





</details>

<details>
<summary>
<code>SHORT_HAIR_FRIZZLE</code>
</summary>





</details>

<details>
<summary>
<code>SHORT_HAIR_SHAGGY_MULLET</code>
</summary>





</details>

<details>
<summary>
<code>SHORT_HAIR_SHORT_CURLY</code>
</summary>





</details>

<details>
<summary>
<code>SHORT_HAIR_SHORT_FLAT</code>
</summary>





</details>

<details>
<summary>
<code>SHORT_HAIR_SHORT_ROUND</code>
</summary>





</details>

<details>
<summary>
<code>SHORT_HAIR_SHORT_WAVED</code>
</summary>





</details>

<details>
<summary>
<code>SHORT_HAIR_SIDES</code>
</summary>





</details>

<details>
<summary>
<code>SHORT_HAIR_THE_CAESAR</code>
</summary>





</details>

<details>
<summary>
<code>SHORT_HAIR_THE_CAESAR_SIDE_PART</code>
</summary>





</details>



</details>



---

### `TopAccessory`

[![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
enum class TopAccessory {
    BLANK,
    KURT,
    PRESCRIPTION01,
    PRESCRIPTION02,
    ROUND,
    SUNGLASSES,
    WAYFARERS,
}
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>BLANK</code>
</summary>





</details>

<details>
<summary>
<code>KURT</code>
</summary>





</details>

<details>
<summary>
<code>PRESCRIPTION01</code>
</summary>





</details>

<details>
<summary>
<code>PRESCRIPTION02</code>
</summary>





</details>

<details>
<summary>
<code>ROUND</code>
</summary>





</details>

<details>
<summary>
<code>SUNGLASSES</code>
</summary>





</details>

<details>
<summary>
<code>WAYFARERS</code>
</summary>





</details>



</details>



---

### `FindBulkRequest`

[![API: Internal/Stable](https://img.shields.io/static/v1?label=API&message=Internal/Stable&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class FindBulkRequest(
    val usernames: List<String>,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>usernames</code>: <code><code><a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/'>List</a>&lt;<a href='https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-string/'>String</a>&gt;</code></code>
</summary>





</details>



</details>



---

### `FindBulkResponse`

[![API: Internal/Stable](https://img.shields.io/static/v1?label=API&message=Internal/Stable&color=red&style=flat-square)](/docs/developer-guide/core/api-conventions.md)



```kotlin
data class FindBulkResponse(
    val avatars: JsonObject,
)
```

<details>
<summary>
<b>Properties</b>
</summary>

<details>
<summary>
<code>avatars</code>: <code><code><a href='https://kotlin.github.io/kotlinx.serialization/kotlinx-serialization-json/kotlinx-serialization-json/kotlinx.serialization.json/-json-object/index.html'>JsonObject</a></code></code>
</summary>





</details>



</details>



---

