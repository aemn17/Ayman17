package org.kevoree.tools.ecore.gencode.model

import org.eclipse.emf.ecore.EPackage
import java.io.{File, FileOutputStream, PrintWriter}
import org.kevoree.tools.ecore.gencode.ProcessorHelper

/**
 * Created by IntelliJ IDEA.
 * User: Gregory NAIN
 * Date: 23/09/11
 * Time: 13:35
 */

trait TraitGenerator {


  def generateContainerTrait(location: String, pack: String, packElement: EPackage) {
    var formatedFactoryName: String = packElement.getName.substring(0, 1).toUpperCase
    formatedFactoryName += packElement.getName.substring(1)
    formatedFactoryName += "Container"

    val pr = new PrintWriter(new File(location + "/" + formatedFactoryName + ".scala"),"utf-8")


    pr.println("package " + pack + ";")
    pr.println()
    //pr.println("import " + pack + ".;")
    pr.println()

    pr.println(ProcessorHelper.generateHeader(packElement))

    //case class name
    pr.println("trait " + formatedFactoryName + " {")
    pr.println()
    pr.println("\t private var internal_eContainer : " + formatedFactoryName + " = null")
    pr.println("\t private var internal_unsetCmd : Option[()=>Any] = None ")

    //generate getter
    pr.println("def eContainer = internal_eContainer")

    //generate setter
    pr.print("\n\t\tdef setEContainer( container : " + formatedFactoryName + ", unsetCmd : Option[()=>Any] ) {\n")
    pr.println("val tempUnsetCmd = internal_unsetCmd")
    pr.println("internal_unsetCmd = None")
    pr.println("tempUnsetCmd.map{inCmd => inCmd() }")
    pr.println("\t\t\t\tthis.internal_eContainer = container\n")
    pr.println("internal_unsetCmd = unsetCmd")
    pr.println("}")
    pr.println("}")
    pr.flush()
    pr.close()
  }

}