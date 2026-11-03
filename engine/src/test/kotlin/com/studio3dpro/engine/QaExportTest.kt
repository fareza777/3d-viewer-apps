package com.studio3dpro.engine

import com.studio3dpro.engine.io.GltfExporter
import org.junit.Test
import java.io.File

class QaExportTest {
    @Test
    fun exportDemoGlbForExternalValidation() {
        val path = System.getenv("QA_OUT") ?: "build/qa/ATLAS_Studio_Demo.glb"
        val file = File(path)
        file.parentFile?.mkdirs()
        file.writeBytes(GltfExporter().exportGlb(DemoScene.create()))
    }
}
