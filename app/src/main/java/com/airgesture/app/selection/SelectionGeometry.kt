package com.airgesture.app.selection

import com.airgesture.app.cursor.CursorPoint

data class SelectionBounds(val left:Float,val top:Float,val right:Float,val bottom:Float){
    val width get()=right-left;val height get()=bottom-top
    val center get()=CursorPoint((left+right)/2,(top+bottom)/2)
    val valid get()=listOf(left,top,right,bottom).all{it.isFinite()} && width>0 && height>0
    fun cell(number:Int):SelectionBounds? {
        if(number !in 1..9 || !valid)return null
        val col=(number-1)%3;val row=(number-1)/3
        return SelectionBounds(left+width*col/3,top+height*row/3,left+width*(col+1)/3,top+height*(row+1)/3)
    }
}
data class NumberTarget(val number:Int,val bounds:SelectionBounds)
data class NumberSnapshot(val id:Long,val windowId:Int,val contextVersion:Long,val targets:List<NumberTarget>,val truncated:Boolean=false)

data class TargetRegion(val number:Int,val bounds:SelectionBounds,val count:Int)

/** Put the instruction in a free horizontal band, never over bottom button numbers. */
fun selectionHintTop(targets:List<NumberTarget>,height:Float,hintHeight:Float,margin:Float):Float? {
    val gaps=mutableListOf<Pair<Float,Float>>();var end=0f
    for(t in targets.sortedBy{it.bounds.top}){
        val top=t.bounds.top.coerceIn(0f,height)
        if(top>end)gaps.add(end to top)
        end=maxOf(end,t.bounds.bottom.coerceIn(0f,height))
    }
    if(end<height)gaps.add(end to height)
    return gaps.lastOrNull{it.second-it.first>=hintHeight+2*margin}?.let{it.second-hintHeight-margin}
}

/** Regions contain actual node targets. An empty region can never become a coordinate click. */
class TargetRegionController {
    private var targets=emptyList<NumberTarget>()
    private var area=SelectionBounds(0f,0f,1f,1f)
    private var screen=SelectionBounds(0f,0f,1f,1f)
    var buttons=emptyList<NumberTarget>();private set
    var regions=emptyList<TargetRegion>();private set
    var choosingButtons=false;private set
    var truncated=false;private set
    fun reset(){targets=emptyList();buttons=emptyList();regions=emptyList();choosingButtons=false;truncated=false}
    fun start(items:List<NumberTarget>,width:Int,height:Int){
        reset();require(width>0 && height>0)
        screen=SelectionBounds(0f,0f,width.toFloat(),height.toFloat());area=screen
        targets=items.filter{it.bounds.valid};rebuild()
    }
    private fun cellOf(target:NumberTarget):Int {
        val p=target.bounds.center
        val col=(((p.x-area.left)/area.width)*3).toInt().coerceIn(0,2)
        val row=(((p.y-area.top)/area.height)*3).toInt().coerceIn(0,2)
        return row*3+col+1
    }
    private fun rebuild(){
        regions=targets.groupBy(::cellOf).toSortedMap().map{(n,items)->TargetRegion(n,checkNotNull(area.cell(n)),items.size)}
    }
    fun chooseRegion(number:Int):Boolean {
        if(choosingButtons || regions.none{it.number==number})return false
        val next=checkNotNull(area.cell(number));targets=targets.filter{cellOf(it)==number};area=next
        if(targets.size<=20 || area.width<screen.width/27 || area.height<screen.height/27){
            choosingButtons=true;buttons=targets.take(20);truncated=targets.size>20;regions=emptyList()
        }else rebuild()
        return true
    }
    fun target(number:Int)=if(choosingButtons)buttons.getOrNull(number-1) else null
    fun displayButtons()=buttons.mapIndexed{i,t->t.copy(number=i+1)}
    fun visibleTargets()=targets
}

class GridSelectionController {
    var bounds=SelectionBounds(0f,0f,1f,1f);private set
    var level=0;private set
    fun reset(){bounds=SelectionBounds(0f,0f,1f,1f);level=0}
    fun select(number:Int):CursorPoint? {
        val next=bounds.cell(number) ?: return null
        bounds=next;level++
        return bounds.center.takeIf{level==2}
    }
}
