package ru.nexora.regions;
import cn.nukkit.utils.Config;
import java.io.File;
final class RegionsStore { final Config cfg; private boolean dirty; RegionsStore(File file){cfg=new Config(file,Config.YAML);} void touch(){dirty=true;} void flush(){if(dirty){cfg.save();dirty=false;}} }
