package org.kevoree.extra.kserial.Utils;

import org.kevoree.extra.kserial.jna.NativeLoader;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by jed
 * User: jedartois@gmail.com
 * Date: 03/02/12
 * Time: 16:14
 */
public class KserialHelper {


    public static List<String> getPortIdentifiers() {
        List<String> ports = new ArrayList<String>();
        File file = new File("/dev");
        File[] files = file.listFiles();
        if (files != null) {
            for (int i = 0; i < files.length; i++)
            {
                if (files[i].isDirectory() == false)
                {
                    if(files[i].getName().startsWith("tty") || files[i].getName().startsWith("cu"))
                    {
                        if(files[i].getName().contains("USB") || files[i].getName().contains("usbserial") || files[i].getName().contains("AC") )
                        {
                            String device_name =   "/dev/"+files[i].getName();
                            if(NativeLoader.getInstance().verify_fd(device_name) == 0)
                            {
                                ports.add(device_name);
                            }
                        }


                    }

                }

            }
        }
        return ports;
    }

}
