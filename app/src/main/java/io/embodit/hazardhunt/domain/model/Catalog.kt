package io.embodit.hazardhunt.domain.model

/**
 * The 28 checkpoints of the bay (design `pois.items`, revision 3). Bay coordinates: origin = the viewer's floor
 * point at placement, -Z ahead, +X right, +Y up, metres. Every hazard has four fixes, the right one first (the card
 * shuffles them per round); the Chinese texts are the design's, the English ones are written to the same length.
 *
 * Pointer boxes are at least 0.25 m on every side (design `entities3d.poiColliders`) and do not overlap where it
 * would hide another checkpoint. The basis lines are teaching hints, not legal citations (design `frame.assumptions`).
 */
object Catalog {
    private fun b(zh: String, en: String) = Bi(zh, en)

    private fun v(x: Float, y: Float, z: Float) = Vec3(x, y, z)

    private fun box(cx: Float, cy: Float, cz: Float, sx: Float, sy: Float, sz: Float) = Box3(Vec3(cx, cy, cz), Vec3(sx, sy, sz))

    private fun fixes(vararg pairs: Pair<String, String>): List<Bi> = pairs.map { Bi(it.first, it.second) }

    val all: List<Poi> =
        listOf(
            Poi(
                "P01", b("通道地面", "Walkway floor"), Zone.FRONT, Level.LOW, v(0.2f, 0f, -1.2f),
                box(0.2f, 0.12f, -1.2f, 0.55f, 0.25f, 0.45f), b("干净地面", "Clean floor"),
                listOf(
                    HazardSpec(
                        "H01", b("油污未清理", "Uncleaned oil spill"), Category.FLOOR,
                        fixes(
                            "立即清理，清理前放湿滑警示牌" to "Clean it now; put a wet-floor sign up first",
                            "先用纸箱盖住，等保洁来再处理" to "Cover it with a box until the cleaner comes",
                            "下班后再统一清，先贴一条防滑胶带" to "Clean at shift end; tape an anti-slip strip now",
                            "在油污周围画一圈粉笔线提醒" to "Draw a chalk line around the spill as a warning",
                        ),
                        b("GB 12801 生产过程安全卫生要求：作业场所地面保持清洁、防滑", "OSHA 1910.22(a)(2): floors kept clean, orderly and dry"),
                    )
                ),
            ),
            Poi(
                "P02", b("工作台底下", "Under the bench"), Zone.FRONT, Level.LOW, v(0.6f, 0.05f, -2.1f),
                box(0.45f, 0.3f, -2.05f, 0.9f, 0.6f, 0.6f), b("线缆走台腿线槽、插线板挂台侧", "Cable in the leg duct, strip on the bench side"),
                listOf(
                    HazardSpec(
                        "H02", b("插线板放地上、延长线横跨通道", "Power strip on floor, cord across walkway"), Category.ELEC,
                        fixes(
                            "线缆架空或走线槽固定，插线板离地" to "Route the cord in a duct; mount the strip off the floor",
                            "用胶带把线沿地面贴平、两头压住" to "Tape the cord flat to the floor at both ends",
                            "换一根更长的线绕着通道边走" to "Use a longer cord that runs round the walkway",
                            "在线上铺一块橡胶垫再贴标识" to "Lay a rubber mat over the cord and add a sign",
                        ),
                        b("GB 12801；AQ/T 7009 机械制造企业安全生产标准化规范：通道无绊倒物、临时线缆固定", "OSHA 1910.305(g)(1): cords are no substitute for fixed wiring; 1910.22(a)(3)"),
                    )
                ),
            ),
            Poi(
                "P03", b("货架底层料箱", "Rack bottom bins"), Zone.LEFT, Level.LOW, v(-1.75f, 0.2f, -1.2f),
                box(-1.62f, 0.25f, -1.2f, 0.75f, 0.4f, 0.5f), b("料箱齐边", "Bins flush with the shelf"),
                listOf(
                    HazardSpec(
                        "H03", b("料箱突出货架 30 cm 到通道", "Bin sticking 30 cm into the aisle"), Category.FLOOR,
                        fixes(
                            "推回层板内，超长物料换层或换架" to "Push it back; move long stock to another rack",
                            "在箱子突出的一角贴一条警示带" to "Put warning tape on the corner that sticks out",
                            "在箱子上放块反光牌让人看见" to "Stand a reflective sign on the bin so it is seen",
                            "没问题，货架旁边本来就常这样" to "Nothing to do, racks always look like this",
                        ),
                        b("GB 12801：通道保持畅通、无突出物", "OSHA 1910.176(a): aisles kept clear, no obstruction"),
                    )
                ),
            ),
            Poi(
                "P04", b("托盘货堆", "Pallet stack"), Zone.RIGHT, Level.LOW, v(1.1f, 0.7f, -1.2f),
                box(1.25f, 0.7f, -1.2f, 1.3f, 1.4f, 1.2f), b("停在通道线外", "Parked outside the lane line"),
                listOf(
                    HazardSpec(
                        "H04", b("托盘停在叉车通道内", "Pallet parked in the forklift lane"), Category.FLOOR,
                        fixes(
                            "移出通道线，停到划定的存放区" to "Move it out of the lane to a marked storage spot",
                            "在托盘上挂一块「临时停放」牌" to "Hang a \"parked for now\" sign on the pallet",
                            "通知叉车司机这几天绕行就行" to "Tell the forklift drivers to go round for a few days",
                            "把黄线往外移半米改画通道" to "Repaint the yellow line half a metre further out",
                        ),
                        b("GB 12801：车行通道不得堆放物料", "OSHA 1910.176(a): passageways kept clear"),
                    )
                ),
            ),
            Poi(
                "P05", b("门口拖把桶", "Mop bucket by the door"), Zone.BACK, Level.LOW, v(0.3f, 0f, 1.5f),
                box(0.35f, 0.45f, 1.5f, 0.9f, 0.9f, 0.7f), b("桶旁「小心地滑」A 字牌、地面干", "Wet-floor sign by the bucket, floor dry"),
                listOf(
                    HazardSpec(
                        "H05", b("地面积水、没有湿滑牌", "Wet floor, no sign"), Category.FLOOR,
                        fixes(
                            "立刻擦干，放「小心地滑」牌" to "Dry it now and put a wet-floor sign up",
                            "把桶挪远一点，让水自己蒸发" to "Move the bucket away and let the water dry",
                            "在门上贴张纸提醒大家小心" to "Tape a note on the door asking people to mind it",
                            "等保洁下一轮过来时再处理" to "Leave it for the cleaner's next round",
                        ),
                        b("GB 12801：湿滑区域应设警示", "OSHA 1910.22(a)(2): floors kept dry; 1910.22(d) hazards corrected"),
                    )
                ),
            ),
            Poi(
                "P06", b("工作台旁废料桶", "Waste bin by the bench"), Zone.FRONT, Level.LOW, v(-0.6f, 0.3f, -2.3f),
                box(-0.6f, 0.32f, -2.3f, 0.42f, 0.64f, 0.42f), b("带盖金属桶", "Metal bin with a lid"),
                listOf(
                    HazardSpec(
                        "H06", b("油棉纱堆在敞口塑料桶", "Oily rags in an open plastic bin"), Category.FIRE,
                        fixes(
                            "换带盖金属桶，每班清空一次" to "Use a lidded metal can, empty it every shift",
                            "再套一层塑料袋把棉纱包起来" to "Wrap the rags in another plastic bag",
                            "把桶放到门外的通风处就行" to "Just put the bin outside where air moves",
                            "往桶里喷点水把棉纱打湿" to "Spray some water on the rags to wet them",
                        ),
                        b("GB 15603 常用化学危险品贮存通则：可燃废弃物密闭收集", "OSHA 1910.22(a)(1) housekeeping; oily waste in covered metal cans (NFPA practice)"),
                    )
                ),
            ),
            Poi(
                "P07", b("气瓶", "Gas cylinder"), Zone.RIGHT, Level.LOW, v(1.25f, 0.8f, -0.3f),
                box(1.25f, 0.75f, -0.3f, 0.4f, 1.5f, 0.4f), b("链条固定在架上、戴瓶帽", "Chained to the stand, cap on"),
                listOf(
                    HazardSpec(
                        "H07", b("气瓶未固定、无瓶帽", "Cylinder unsecured, no cap"), Category.FIRE,
                        fixes(
                            "链条固定到架上，装瓶帽" to "Chain it to the stand and fit the cap",
                            "靠墙放着，旁边贴一张「勿碰」" to "Lean it on the wall with a \"do not touch\" note",
                            "放倒在地上，这样不会倒下来" to "Lay it on the floor so it cannot fall over",
                            "用绳子绕一圈系在工作台腿上" to "Tie it to a bench leg with a piece of rope",
                        ),
                        b("GB/T 34525 气瓶搬运、装卸、储存和使用安全规定：直立固定、戴帽", "OSHA 1910.101(b) / CGA P-1: cylinders secured, caps in place"),
                    )
                ),
            ),
            Poi(
                "P08", b("台面角磨机", "Angle grinder on bench"), Zone.FRONT, Level.MID, v(0.3f, 0.95f, -2.25f),
                box(0.3f, 0.98f, -2.22f, 0.26f, 0.25f, 0.25f), b("防护罩完整", "Wheel guard in place"),
                listOf(
                    HazardSpec(
                        "H08", b("角磨机防护罩被拆", "Grinder guard removed"), Category.MACH,
                        fixes(
                            "装回防护罩再使用，拆罩者停用" to "Refit the guard before use; stop the misuse",
                            "戴厚手套和面罩就可以继续用" to "Keep using it with thick gloves and a visor",
                            "换一片小一号的砂轮片再用" to "Fit a smaller wheel and carry on using it",
                            "降低转速、慢一点用就没事" to "Run it slower and work carefully, that is fine",
                        ),
                        b("GB/T 3787 手持式电动工具的管理、使用、检查和维修安全技术规程：防护装置不得拆除", "OSHA 1910.243(c)(1): portable abrasive wheels guarded"),
                    )
                ),
            ),
            Poi(
                "P09", b("台面化学品瓶", "Bottle on bench"), Zone.FRONT, Level.MID, v(0.6f, 1.0f, -2.45f),
                box(0.6f, 1.03f, -2.45f, 0.25f, 0.26f, 0.25f), b("有标签的清洗剂", "Labelled cleaner"),
                listOf(
                    HazardSpec(
                        "H09", b("无标签的液体瓶", "Unlabelled liquid bottle"), Category.CHEM,
                        fixes(
                            "停用贴安全标签，不明液体按废液处理" to "Stop using it; label it or treat it as waste",
                            "贴张纸条写「待确认」，先放回原位" to "Stick a \"to check\" note on it, put it back",
                            "闻一下判断是什么，再决定怎么用" to "Smell it to find out what it is, then decide",
                            "倒进下水道冲掉，瓶子扔普通垃圾" to "Pour it down the drain, bin the bottle",
                        ),
                        b("GB 15258 化学品安全标签编写规定：容器必须有安全标签", "OSHA 1910.1200(f)(6): workplace labelling"),
                    )
                ),
            ),
            Poi(
                "P10", b("台面美工刀", "Utility knife on bench"), Zone.FRONT, Level.MID, v(0.6f, 0.92f, -2.0f),
                box(0.6f, 0.95f, -2.0f, 0.26f, 0.25f, 0.26f), b("刀片收回、放刀架", "Blade retracted, knife in its holder"),
                listOf(
                    HazardSpec(
                        "H10", b("美工刀刀片外露在台沿", "Knife blade exposed at the bench edge"), Category.MACH,
                        fixes(
                            "收回刀片，放回刀架，刀刃朝里" to "Retract the blade, put it in the holder",
                            "转个方向让刀刃朝里放在台上" to "Turn it so the blade points into the bench",
                            "用一张纸把刀片包起来放着" to "Wrap the blade in a sheet of paper",
                            "放到台面最里面，远离台沿" to "Push it to the back, away from the edge",
                        ),
                        b("GB 12801：刃具使用后归位", "OSHA 1910.242(a): hand tools kept in safe condition"),
                    )
                ),
            ),
            Poi(
                "P11", b("台面插座", "Bench power outlet"), Zone.FRONT, Level.MID, v(0.03f, 1.05f, -2.5f),
                box(0.03f, 1.04f, -2.47f, 0.26f, 0.28f, 0.25f), b("一个插线板插在墙插", "One strip plugged into the wall"),
                listOf(
                    HazardSpec(
                        "H11", b("插线板串插线板", "Daisy-chained power strips"), Category.ELEC,
                        fixes(
                            "拆掉串接，大功率单独回路" to "Unchain them; heavy loads on their own circuit",
                            "换一根粗一点的线再串接" to "Chain them again with a thicker cord",
                            "在串接处加一个保险丝就行" to "Add a fuse where the strips are chained",
                            "少插几个设备，功率不超就行" to "Plug in fewer tools so the load is lower",
                        ),
                        b("GB/T 13869 用电安全导则：移动式插座不得串接、不得超载", "OSHA 1910.303(b)(2): equipment used as listed"),
                    )
                ),
            ),
            Poi(
                "P12", b("灭火器", "Fire extinguisher"), Zone.BACK, Level.MID, v(1.2f, 1.0f, 1.95f),
                box(1.21f, 0.7f, 1.78f, 0.38f, 1.4f, 0.42f), b("挂钩上、压力表绿、检查卡在期", "On its hook, gauge green, tag current"),
                listOf(
                    HazardSpec(
                        "H12", b("灭火器被纸箱挡住", "Extinguisher blocked by boxes"), Category.FIRE,
                        fixes(
                            "移开纸箱，保持 1 m 净空并画线" to "Move the boxes, keep 1 m clear and mark it",
                            "把灭火器挪到纸箱顶上放着" to "Put the extinguisher on top of the boxes",
                            "在纸箱上贴一个指示箭头" to "Stick an arrow on the boxes pointing to it",
                            "在门口多买一个灭火器备着" to "Buy another extinguisher for the doorway",
                        ),
                        b("GB 50140 建筑灭火器配置设计规范：设置点不得被遮挡", "OSHA 1910.157(c)(1): readily accessible"),
                    ),
                    HazardSpec(
                        "H12b", b("灭火器压力表指红、检查卡过期", "Gauge in the red, tag expired"), Category.FIRE,
                        fixes(
                            "换新 / 送检，并补检查记录" to "Replace or service it and log the check",
                            "摇一摇让干粉松动再挂回去" to "Shake it to loosen the powder, hang it back",
                            "贴一张「待检」纸条先挂着" to "Leave it up with a \"to inspect\" note",
                            "放着不动，红区也还能用一次" to "Leave it; in the red it still works once",
                        ),
                        b("GB 50444 建筑灭火器配置验收及检查规范：压力不足立即维修", "OSHA 1910.157(e)(2): monthly visual inspection"),
                    ),
                ),
            ),
            Poi(
                "P13", b("配电箱", "Electrical panel"), Zone.LEFT, Level.MID, v(-1.95f, 1.4f, -0.2f),
                box(-1.88f, 1.4f, -0.2f, 0.25f, 0.65f, 0.45f), b("箱门关、挂「当心触电」标识", "Door shut, shock warning sign"),
                listOf(
                    HazardSpec(
                        "H13", b("配电箱门敞开、线头外露", "Panel door open, exposed wires"), Category.ELEC,
                        fixes(
                            "停电处理线头、关门上锁挂牌" to "Isolate, fix the wires, lock and tag the door",
                            "用绝缘胶带把线头缠一下再关门" to "Wrap the ends in tape and close the door",
                            "把门掩上，贴一张「危险」纸" to "Push the door to and tape up a \"danger\" note",
                            "在箱前放一块「请勿靠近」牌" to "Stand a \"keep away\" sign in front of it",
                        ),
                        b("GB 50054 低压配电设计规范：带电部位应有防护", "OSHA 1910.303(g)(2)(i): live parts guarded"),
                    )
                ),
            ),
            Poi(
                "P14", b("安全出口", "Emergency exit"), Zone.BACK, Level.MID, v(-0.75f, 1.0f, 1.9f),
                box(-0.75f, 1.05f, 1.7f, 0.95f, 2.1f, 0.6f), b("门前空、门可开", "Doorway clear, door opens"),
                listOf(
                    HazardSpec(
                        "H14", b("出口被托盘和纸箱堵住", "Exit blocked by a pallet and boxes"), Category.FIRE,
                        fixes(
                            "清空出口通道并划线禁放" to "Clear the exit route and mark it no-storage",
                            "留一条小缝能过人就可以" to "Leave a gap wide enough to squeeze through",
                            "贴「勿堵」标识，货物暂时不动" to "Put up a \"keep clear\" sign, leave the goods",
                            "让大家从另一个门走就行" to "Tell everyone to use the other door",
                        ),
                        b("GB 50016 建筑设计防火规范：疏散通道不得占用", "OSHA 1910.37(a)(3): exit routes free and unobstructed"),
                    )
                ),
            ),
            Poi(
                "P15", b("货架中层", "Rack middle tier"), Zone.LEFT, Level.MID, v(-1.75f, 1.3f, -1.6f),
                box(-1.75f, 1.0f, -1.6f, 0.45f, 0.9f, 0.5f), b("重物在下、轻物在上", "Heavy below, light above"),
                listOf(
                    HazardSpec(
                        "H15", b("重桶放上层、轻纸箱压底层", "Heavy drum up top, light boxes below"), Category.STORE,
                        fixes(
                            "重物换到下层，轻物上层" to "Move the heavy drum down, light boxes up",
                            "用捆扎带把上层的桶绑住" to "Strap the drum on the upper tier",
                            "在下层纸箱下面垫一块木板" to "Put a board under the boxes below",
                            "没事，这种货架承重很大" to "Fine as it is, the rack takes a lot of weight",
                        ),
                        b("GB/T 27924 工业货架规格尺寸与额定荷载：按额定荷载、重下轻上", "OSHA 1910.176(b): materials stored to prevent collapse"),
                    )
                ),
            ),
            Poi(
                "P16", b("货架溶剂桶", "Solvent drum on rack"), Zone.LEFT, Level.MID, v(-1.75f, 0.85f, -1.05f),
                box(-1.75f, 0.86f, -1.05f, 0.4f, 0.42f, 0.35f), b("桶盖拧紧、有标签", "Lid tight, labelled"),
                listOf(
                    HazardSpec(
                        "H16", b("溶剂桶敞口", "Open solvent drum"), Category.CHEM,
                        fixes(
                            "盖紧桶盖，移到防爆柜里存放" to "Close the lid, store it in a safety cabinet",
                            "用一块布盖住桶口先放着" to "Lay a cloth over the opening for now",
                            "把桶放到地上离货架远一点" to "Put the drum on the floor away from the rack",
                            "开窗通风，味道散了就没事" to "Open a window; once the smell is gone, fine",
                        ),
                        b("GB 15603：易燃液体密闭贮存", "OSHA 1910.106(e)(2): flammable liquids in closed containers"),
                    )
                ),
            ),
            Poi(
                "P17", b("梯子", "Ladder"), Zone.LEFT, Level.MID, v(-1.85f, 0.9f, 0.3f),
                box(-1.75f, 0.9f, 0.3f, 0.45f, 1.8f, 0.6f), b("折叠立放、脚垫完整", "Folded upright, all feet on"),
                listOf(
                    HazardSpec(
                        "H17", b("梯子单边斜靠隔断、一只脚垫缺", "Ladder leaning on partition, a foot missing"), Category.MACH,
                        fixes(
                            "停用补脚垫，使用时全开锁定" to "Tag it out, fit the foot, open and lock it",
                            "靠紧货架一点，上去时小心" to "Lean it closer to the rack and climb carefully",
                            "找一个人在下面扶着就行" to "Have someone hold it at the bottom",
                            "在梯子上贴一张「小心」纸" to "Stick a \"careful\" note on the ladder",
                        ),
                        b("GB 12142 便携式金属梯安全要求", "OSHA 1910.23(b): ladders inspected, defective ones tagged out"),
                    )
                ),
            ),
            Poi(
                "P18", b("急救箱", "First-aid kit"), Zone.BACK, Level.MID, v(0.85f, 1.4f, 1.95f),
                box(0.85f, 1.42f, 1.9f, 0.3f, 0.36f, 0.25f), b("满、在期、有标识", "Full, in date, signed"),
                listOf(
                    HazardSpec(
                        "H18", b("急救箱空、药品过期", "First-aid kit empty, expired"), Category.PPE,
                        fixes(
                            "补齐并登记检查日期" to "Restock it and log the check date",
                            "贴张纸「药品去办公室拿」" to "Tape on a note: \"supplies in the office\"",
                            "把空箱子拆掉，免得误导人" to "Take the empty box down so no one is misled",
                            "放一瓶酒精和几片创可贴" to "Put in a bottle of alcohol and a few plasters",
                        ),
                        b("GBZ 1 工业企业设计卫生标准：配备急救用品并定期检查", "OSHA 1910.151(b): adequate first-aid supplies readily available"),
                    )
                ),
            ),
            Poi(
                "P19", b("磨削工位护目镜钩", "Goggle hook at grinding station"), Zone.FRONT, Level.MID, v(0.25f, 1.9f, -2.58f),
                box(0.2f, 1.88f, -2.5f, 0.4f, 0.3f, 0.25f), b("钩上有护目镜 + 「必须戴护目镜」标识", "Goggles on the hook, eye-protection sign"),
                listOf(
                    HazardSpec(
                        "H19", b("没有护目镜、没有标识", "No goggles, no sign"), Category.PPE,
                        fixes(
                            "配护目镜，挂「必须戴护目镜」牌" to "Supply goggles, hang a \"wear goggles\" sign",
                            "在台面贴一张提醒便签就行" to "Stick a reminder note on the bench top",
                            "口头提醒大家磨削时小心点" to "Tell people to be careful when grinding",
                            "把磨削工作换到别的工位做" to "Move the grinding work to another bench",
                        ),
                        b("GB 2894 安全标志及其使用导则；GB/T 11651 个体防护装备选用规范", "OSHA 1910.133(a)(1): eye protection where particles fly"),
                    )
                ),
            ),
            Poi(
                "P20", b("货架顶层", "Rack top tier"), Zone.LEFT, Level.HIGH, v(-1.75f, 2.25f, -1.9f),
                box(-1.55f, 2.0f, -1.9f, 0.85f, 0.5f, 0.4f), b("纸箱齐边、有挡板", "Boxes flush, edge guard fitted"),
                listOf(
                    HazardSpec(
                        "H20", b("货架顶纸箱突出边缘 40 cm", "Boxes overhanging the top tier by 40 cm"), Category.STORE,
                        fixes(
                            "推回并加挡板 / 限高" to "Push back; fit an edge guard and height limit",
                            "在突出的纸箱上贴警示带" to "Put warning tape on the overhanging boxes",
                            "在下面地上放一块软垫" to "Lay a soft mat on the floor below",
                            "没事，纸箱很轻砸不到人" to "Fine, boxes are light and will not hurt",
                        ),
                        b("GB/T 27924：顶层不得超出层板", "OSHA 1910.176(b): stacked materials stable and secure"),
                    )
                ),
            ),
            Poi(
                "P21", b("货架顶堆叠", "Rack top stacking"), Zone.LEFT, Level.HIGH, v(-1.75f, 2.15f, -1.1f),
                box(-1.75f, 2.0f, -1.1f, 0.45f, 0.5f, 0.45f), b("两箱平稳", "Two boxes, level"),
                listOf(
                    HazardSpec(
                        "H21", b("重箱压在半塌的轻箱上倾斜", "Heavy box on a crushed light box, tilting"), Category.STORE,
                        fixes(
                            "卸下重箱，换到下层重新码放" to "Take the heavy box down, restack it lower",
                            "再垫一个空箱把它撑平就行" to "Wedge an empty box under it to level it",
                            "用绳子把上面的箱子拉住固定" to "Tie the top box back with a rope",
                            "把整堆挪到货架靠墙一侧去" to "Shift the whole stack against the wall side",
                        ),
                        b("GB/T 27924：货物堆码平稳", "OSHA 1910.176(b): stacks stable and secure"),
                    )
                ),
            ),
            Poi(
                "P22", b("隔断顶日光灯", "Light fixture on partition"), Zone.FRONT, Level.HIGH, v(0.6f, 2.25f, -2.58f),
                box(0.6f, 2.24f, -2.52f, 1.25f, 0.26f, 0.25f), b("灯罩完整", "Cover intact"),
                listOf(
                    HazardSpec(
                        "H22", b("灯罩破、灯管一头脱出悬着", "Broken cover, tube hanging loose"), Category.ELEC,
                        fixes(
                            "断电更换，装防坠灯罩" to "Switch off, replace it, fit a retaining cover",
                            "用胶带把灯管粘回去固定" to "Tape the tube back into its holder",
                            "把这盏灯关掉，用旁边的灯" to "Switch this one off and use the next lamp",
                            "换成 LED 灯管就不会掉" to "Swap in an LED tube so it cannot fall",
                        ),
                        b("GB 7000.1 灯具 第1部分：一般要求与试验", "OSHA 1910.303(b)(1): equipment free from recognised hazards"),
                    )
                ),
            ),
            Poi(
                "P23", b("线槽裸接头", "Exposed splice on cable tray"), Zone.FRONT, Level.HIGH, v(-1.2f, 2.25f, -2.58f),
                box(-1.2f, 2.25f, -2.52f, 0.5f, 0.35f, 0.25f), b("线槽盖板完整", "Tray cover intact"),
                listOf(
                    HazardSpec(
                        "H23", b("线槽上一处裸接头悬在货架上方", "Bare splice hanging above the rack"), Category.ELEC,
                        fixes(
                            "断电用接线盒重接" to "Isolate and remake it inside a junction box",
                            "在接头处多缠几圈绝缘胶带" to "Wrap a few more turns of tape round it",
                            "把接头挂高一点离货架远些" to "Hang the splice higher, away from the rack",
                            "在货架顶贴一张「勿碰」纸" to "Tape a \"do not touch\" note on the rack top",
                        ),
                        b("GB 50054：导线接头应在接线盒内", "OSHA 1910.303(g)(2)(i); 1910.305(b)(2)"),
                    )
                ),
            ),
            Poi(
                "P24", b("货架横梁 / 立柱", "Rack beam / upright"), Zone.LEFT, Level.HIGH, v(-1.5f, 2.05f, -1.5f),
                box(-1.5f, 1.95f, -1.5f, 0.25f, 0.4f, 0.3f), b("有限载牌、立柱直", "Load sign on, upright straight"),
                listOf(
                    HazardSpec(
                        "H24", b("无限载标识、立柱撞弯", "No load sign, upright bent"), Category.STORE,
                        fixes(
                            "卸货停用，换立柱并挂限载牌" to "Unload it, replace the upright, hang a load sign",
                            "用铁丝把弯的地方绑一下" to "Tie wire round the bent part of the upright",
                            "少放点东西，先凑合用着" to "Put less on it and make do for now",
                            "在立柱上贴一张「小心」纸" to "Stick a \"careful\" note on the upright",
                        ),
                        b("GB/T 27924：挂限载牌、变形立柱停用", "OSHA 1910.176(b): storage safe and secure"),
                    )
                ),
            ),
            Poi(
                "P25", b("出口指示灯", "Exit sign light"), Zone.BACK, Level.HIGH, v(-0.75f, 2.3f, 1.95f),
                box(-0.75f, 2.3f, 1.88f, 0.45f, 0.25f, 0.25f), b("常亮", "Lit"),
                listOf(
                    HazardSpec(
                        "H25", b("出口指示灯不亮", "Exit sign dark"), Category.FIRE,
                        fixes(
                            "报修更换，修好前贴反光出口标" to "Report and replace; reflective sign meanwhile",
                            "拍一拍灯箱，可能只是接触不良" to "Tap the box, it is probably a loose contact",
                            "把安全门开着，不用灯也看得见" to "Prop the door open so no light is needed",
                            "用贴纸在门上写一个「出口」" to "Write \"EXIT\" on the door with a sticker",
                        ),
                        b("GB 17945 消防应急照明和疏散指示系统", "OSHA 1910.37(b)(6): exit signs illuminated"),
                    )
                ),
            ),
            Poi(
                "P26", b("工作台右半台面", "Bench right side"), Zone.FRONT, Level.MID, v(1.4f, 0.95f, -2.3f),
                box(1.4f, 0.98f, -2.3f, 0.5f, 0.25f, 0.4f), b("台钳与整齐工具", "Vice and tidy tools"),
                listOf(
                    HazardSpec(
                        "H26", b("台钳旁敞口溶剂罐", "Open solvent can by the vice"), Category.CHEM,
                        fixes(
                            "盖紧，用多少倒多少，放防爆柜" to "Lid it, pour only what you need, store it",
                            "推到台面最里面，离台钳远一点" to "Push it to the back, away from the vice",
                            "开风扇把味道吹散就可以" to "Turn on a fan to blow the fumes away",
                            "在罐子上贴一张标签就行" to "Just put a label on the can",
                        ),
                        b("GB 15603：易燃液体密闭贮存", "OSHA 1910.106(e)(2): closed containers"),
                    )
                ),
                challengeOnly = true,
            ),
            Poi(
                "P27", b("货架后角落", "Corner behind the rack"), Zone.LEFT, Level.MID, v(-1.85f, 1.3f, -2.35f),
                box(-1.86f, 1.3f, -2.35f, 0.3f, 1.0f, 0.45f), b("角落空、消防栓箱可见", "Corner clear, hose cabinet visible"),
                listOf(
                    HazardSpec(
                        "H27", b("消防栓箱被空托盘挡住", "Hose cabinet blocked by empty pallets"), Category.FIRE,
                        fixes(
                            "清出托盘，栓箱前保持净空" to "Clear the pallets, keep the cabinet front free",
                            "把栓箱的门拆了方便拿取" to "Take the cabinet door off for easier access",
                            "在托盘上挂一个指示箭头" to "Hang an arrow on the pallets pointing to it",
                            "在别处再装一个消防栓箱" to "Fit another hose cabinet somewhere else",
                        ),
                        b("GB 50974 消防给水及消火栓系统技术规范：消火栓箱前不得遮挡", "OSHA 1910.158(c)(1): hose cabinets accessible"),
                    )
                ),
                challengeOnly = true,
            ),
            Poi(
                "P28", b("叉车通道转角镜", "Lane corner mirror"), Zone.RIGHT, Level.HIGH, v(1.9f, 2.1f, 1.0f),
                box(1.85f, 2.05f, 1.0f, 0.3f, 0.4f, 0.4f), b("凸面镜在、干净", "Convex mirror in place, clean"),
                listOf(
                    HazardSpec(
                        "H28", b("转角镜缺失，只剩支架", "Corner mirror missing, bracket only"), Category.PPE,
                        fixes(
                            "补装凸面镜并定期擦拭" to "Refit a convex mirror and clean it regularly",
                            "让叉车经过转角时按喇叭" to "Have forklifts sound the horn at the corner",
                            "在转角贴一张「慢行」标识" to "Put a \"slow\" sign up at the corner",
                            "在地上画一条停止线" to "Paint a stop line on the floor",
                        ),
                        b("GB 2894 安全标志及其使用导则；视线死角设凸面镜（厂内通行惯例）", "OSHA 1910.178(n)(4): slow down and sound horn where vision is obstructed"),
                    )
                ),
            ),
        )

    private val byId: Map<String, Poi> = all.associateBy { it.id }

    fun get(id: String): Poi? = byId[id.uppercase()]

    /** The 26 regular checkpoints a round draws from. */
    val regular: List<Poi> = all.filter { !it.challengeOnly }

    /** The two walk-mode checkpoints (P26, P27), only placed in walk mode. */
    val challenge: List<Poi> = all.filter { it.challengeOnly }
}
