package com.hoggamers.rankforge.domain.ocr.parsing

import com.hoggamers.rankforge.domain.ocr.extraction.*
import com.hoggamers.rankforge.domain.ocr.layout.*

enum class PlacementParseStatus { DETECTED, MISSING, AMBIGUOUS, DUPLICATE, INVALID }
data class PlacementOcrEvidence(val text:String,val geometry:RawOcrGeometry?,val source:RawOcrExtractionResult)
data class ParsedPlacementRow(val expectedPlacementId:Int,val panelId:ScoreboardPanelId,val rowIndex:Int,val status:PlacementParseStatus,val detectedValue:Int?,val evidence:List<PlacementOcrEvidence>)
data class PlacementParsingInput(val extractions:List<RawOcrExtractionResult>,val layout:ScoreboardLayoutDefinition=FreeFireMaxScoreboardLayout.definition)
data class PlacementParsingResult(val rows:List<ParsedPlacementRow>)
interface PlacementParser { fun parse(input:PlacementParsingInput):PlacementParsingResult }

class FixedLayoutPlacementParser:PlacementParser {
 override fun parse(input:PlacementParsingInput):PlacementParsingResult {
  val rows=input.layout.panels.flatMap { p->p.rows.map { r->Triple(p,r,zone(p,r,input.layout)) } }
  val all=input.extractions.filterIsInstance<RawOcrExtractionResult.Extracted>().flatMap { x->entities(x).map { PlacementOcrEvidence(it.first,it.second,x) } }
  val parsed=rows.map { (p,r,z)->
   val e=all.filter { it.geometry?.boundingBox?.let { b-> b.left < z.x+z.width && b.right > z.x && b.top < z.y+z.height && b.bottom > z.y }==true }
   val values=e.mapNotNull { token(it.text) }.distinct()
   val status=when { values.size>1->PlacementParseStatus.AMBIGUOUS; values.size==1->PlacementParseStatus.DETECTED; e.isEmpty()->PlacementParseStatus.MISSING; else->PlacementParseStatus.INVALID }
   ParsedPlacementRow(r.placementId,p.id,r.rowIndex,status,values.singleOrNull(),e)
  }
  val duplicates=parsed.filter { it.status==PlacementParseStatus.DETECTED }.groupBy { it.detectedValue }.filterValues { it.size>1 }.keys
  return PlacementParsingResult(parsed.map { if(it.detectedValue in duplicates) it.copy(status=PlacementParseStatus.DUPLICATE) else it })
 }
 private fun entities(x:RawOcrExtractionResult.Extracted)=x.blocks.flatMap { b->b.lines.flatMap { l->if(l.elements.isEmpty()) listOf(l.text to l.geometry) else l.elements.map { it.text to it.geometry } } }
 private fun token(text:String):Int? { val t=text.trim().removeSuffix(".").replace('O','0').replace('o','0'); return t.toIntOrNull()?.takeIf { it in 1..12 } }
 private fun zone(p:ScoreboardPanelDefinition,r:ScoreboardRowDefinition,l:ScoreboardLayoutDefinition):OcrPixelRect { val w=l.calibrationWidth;val h=l.calibrationHeight;val py=(p.contentRect.y*h).toInt();val ph=(p.contentRect.height*h).toInt();val rowH=ph/p.rows.size;return OcrPixelRect((p.contentRect.x*w).toInt(),py+r.rowIndex*rowH,((p.contentRect.width*.12)*w).toInt(),rowH) }
}
