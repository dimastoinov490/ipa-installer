package com.ipainstaller

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object SampleIpaBuilder {

    fun createSampleIpa(destinationDir: File, appTitle: String = "TestApp", bundleId: String = "com.example.testapp"): File {
        destinationDir.mkdirs()
        val file = File(destinationDir, "$appTitle.ipa")

        val infoPlistContent = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleDevelopmentRegion</key>
    <string>en</string>
    <key>CFBundleDisplayName</key>
    <string>$appTitle</string>
    <key>CFBundleExecutable</key>
    <string>$appTitle</string>
    <key>CFBundleIdentifier</key>
    <string>$bundleId</string>
    <key>CFBundleInfoDictionaryVersion</key>
    <string>6.0</string>
    <key>CFBundleName</key>
    <string>$appTitle</string>
    <key>CFBundlePackageType</key>
    <string>APPL</string>
    <key>CFBundleShortVersionString</key>
    <string>1.0.0</string>
    <key>CFBundleVersion</key>
    <string>1</string>
    <key>LSRequiresIPhoneOS</key>
    <true/>
</dict>
</plist>""".trimIndent()

        FileOutputStream(file).use { fos ->
            ZipOutputStream(fos).use { zos ->
                // Directory: Payload/
                val dirEntry = ZipEntry("Payload/")
                zos.putNextEntry(dirEntry)
                zos.closeEntry()

                // Directory: Payload/DemoApp.app/
                val appDirEntry = ZipEntry("Payload/$appTitle.app/")
                zos.putNextEntry(appDirEntry)
                zos.closeEntry()

                // Info.plist
                val plistEntry = ZipEntry("Payload/$appTitle.app/Info.plist")
                zos.putNextEntry(plistEntry)
                zos.write(infoPlistContent.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                // Dummy binary executable placeholder
                val execEntry = ZipEntry("Payload/$appTitle.app/$appTitle")
                zos.putNextEntry(execEntry)
                zos.write("DUMMY_MACHO_EXECUTABLE_SAMPLE".toByteArray())
                zos.closeEntry()
            }
        }

        return file
    }
}
