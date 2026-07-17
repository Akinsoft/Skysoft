package com.skysoft.features.pets

internal val mainPetMenuNamePattern = Regex("""(?:\(\d+/\d+\) )?Pets(?:: ".*")?(?: \(\d+/\d+\))? ?""")
internal val petMenuPetStackNamePattern =
    Regex(
        """(?:§.)*\[Lvl (?<level>[\d,]+)] """ +
            """(?:(?:§.)+\[(?:§.)*\d+(?:§.)*(?<altskin>§.✦)(?:§.)*] )?""" +
            """(?:§.)*§(?<rarity>.)(?<pet>[^§]+?)(?<skin>§. ✦)?""",
    )
internal val petTabWidgetNamePattern =
    Regex(""" \[Lvl (?<level>[\d,]+)] (?:\[\d+(?<altskin>✦)] )?(?<pet>[\w -]+?)(?:(?<skin> ✦))?$""")
internal val petTabWidgetXpPattern =
    Regex(""" (?:(?<max>MAX LEVEL)|(?:\+)?(?<current>[\d,.kM]+)(?:(?:|/)*(?<next>[\d,.kM]+))? XP(?: \((?<percentage>[\d.]+)%\))?)""")
internal val petMenuSelectedPetNamePattern =
    Regex("""(?:§.)+Selected pet: §(?<rarity>[^c])(?<pet>[\w -]+)(?<skin>§. ✦)?""")
internal val petMenuSelectedPetProgressPattern =
    Regex("""(?:§.)+(?:MAX LEVEL|Progress to Level (?<next>\d+): (?:§.)+(?<percentage>[\d.]+)%)""")
internal val petMenuSelectedPetXpPattern =
    Regex("""(?:§.|▸| )+(?<current>[\d,.kM]+)(?: XP|(?:§.|/)+(?<next>[\d,.kM]+))""")
internal val autoPetMessagePattern =
    Regex(
        """§cAutopet §eequipped your §7\[Lvl (?<level>\d+)] """ +
            """(?:(?:§.)+\[(?:§.)*\d+(?:§.)*(?<altskin>§.✦)(?:§.)*] )?""" +
            """(?:§.)*§(?<rarity>.)(?<pet>[^§]+)(?<skin>§. ✦)?§e! §a§lVIEW RULE""",
    )
internal val autoPetHoverHeldItemPattern = Regex(""".*Held Item: (?<item>.*)""")
internal val petItemHeldMessagePattern =
    Regex("""(?:§a)?Your pet is now holding (?<item>.+?)(?:§r)?(?:§a)?\.""")
internal val petMenuAddSuccessPattern = Regex("""Successfully added (?<pet>.+) to your pet menu!""")
