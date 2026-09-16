package com.airgesture.app.training

import com.airgesture.app.accessibility.ActionKind

data class TrainingStep(val id:String,val instruction:String,val expected:ActionKind?,val expectedCount:Int,val target:Int?=null){
    val label get()=when(id){"up"->"四指上滑";"down"->"两指下滑";"click"->"拇指靠近两次点击";"index_only"->"只移动食指";"single_approach"->"拇指只靠近一次";"relax"->"自然放松与收手";"left"->"向左切页";"right"->"向右切页";"back"->"返回";"home"->"手势 6 桌面";"like"->"拇指上移两次";"dislike"->"拇指下移两次";else->id}
}
object TrainingPlan {
    val angles=listOf("掌心正对前摄","手掌向左偏约 20°，整手入镜","手掌向右偏约 20°，整手入镜")
    val steps=listOf(
        TrainingStep("up","四指朝上，连续向上滑两次，中间自然收手，不用停。",ActionKind.SWIPE_UP,2),
        TrainingStep("down","只伸食指和中指，指尖始终朝上，整只手连续向下移动两次，中间自然抬回，不用停。",ActionKind.SWIPE_DOWN,2),
        TrainingStep("click","单独伸食指，瞄准 3 号靶心。连贯地做：拇指靠近、稍张开、再靠近，不必碰到；黄 1 表示第一次，绿 2 表示点击。两次之间不要移走整只手。",ActionKind.CLICK,1,3),
        TrainingStep("index_only","只移动食指瞄准几个靶心，拇指不动；这段不应该点击。",null,0),
        TrainingStep("single_approach","食指瞄准 3 号，只让拇指靠近一次并保持；这段不应该点击。",null,0,3),
        TrainingStep("relax","手掌放松、自然收手再入镜；不做命令，观察是否误触。",null,0),
        TrainingStep("left","五指全部张开、指尖朝上，整只手向左平移一次，再保持手形向右收回；收回不应右滑。",ActionKind.SWIPE_LEFT,1),
        TrainingStep("right","只伸食指和中指、指尖朝上，整只手向右平移一次，再保持手形向左收回；收回不应左滑。",ActionKind.SWIPE_RIGHT,1),
        TrainingStep("back","四指指尖转向侧面，横推一次，模拟返回。",ActionKind.BACK,1),
        TrainingStep("home","伸拇指和小指比 6，保持半秒，模拟桌面。",ActionKind.HOME,1),
        TrainingStep("like","其余四指收起，伸拇指，向上移动、收回、再向上移动。",ActionKind.LIKE,1),
        TrainingStep("dislike","其余四指收起，伸拇指，向下移动、收回、再向下移动。",ActionKind.DISLIKE,1)
    )
    val count get()=steps.size*angles.size
    fun step(index:Int)=steps[index%steps.size]
    fun angle(index:Int)=angles[index/steps.size]
}
