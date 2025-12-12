# AxMinions - ValdoriaCraft Fork

<img width="1920" height="1080" alt="axminions-banner" src="https://github.com/user-attachments/assets/384c8403-801e-431c-8bab-30a9e60aec14" />

## 🔱 About This Fork

This is a **custom fork** of [AxMinions](https://github.com/Artillex-Studios/AxMinions) specifically optimized and maintained for **ValdoriaCraft** Minecraft server.

### ⚠️ Important Notice

- **Primary Focus:** ValdoriaCraft server performance and requirements
- **Support:** We do **NOT** provide support for other servers using this fork
- **License:** You are free to use and modify the code for your own purposes
- **Contributions:** Pull requests are welcome, but priority is given to ValdoriaCraft-specific needs

## 🚀 ValdoriaCraft Optimizations

This fork includes several performance enhancements specifically designed for ValdoriaCraft's scale:

### Performance Improvements
- ✅ **Batch Processing System** - Processes minions in configurable batches to prevent lag spikes
- ✅ **O(1) Chunk Lookups** - HashMap-based chunk management for instant lookups
- ✅ **Thread-Safe Operations** - ConcurrentHashMap and queue-based processing
- ✅ **Configurable Tick Rate** - Adjust minion processing based on server load
- ✅ **Bug Fixes** - Fixed interaction issues and GUI problems

### Configuration
```yaml
# How many minions should be processed per tick
# Optimized for ValdoriaCraft's server capacity
ticker-batch-size: 50
```

See [PERFORMANCE_OPTIMIZATIONS.md](PERFORMANCE_OPTIMIZATIONS.md) for detailed information.

## 📦 Installation

1. Download the latest release from the releases page
2. Place the JAR file in your server's `plugins` folder
3. Restart your server
4. Configure `config.yml` to your needs

## 🛠️ Building from Source

```bash
./gradlew build
```

The compiled JAR will be in `build/libs/`

## 📚 Original Project

This fork is based on **AxMinions** by Artillex Studios:
- **Original Repository:** https://github.com/Artillex-Studios/AxMinions
- **Bug Reports (Original):** https://github.com/Artillex-Studios/Issues
- **Support (Original):** https://dc.artillex-studios.com/

## 📄 License

This project maintains the same license as the original AxMinions project. Please refer to the LICENSE file for details.

## 🤝 Contributing

While this fork is primarily maintained for ValdoriaCraft, we welcome contributions that:
- Improve performance
- Fix bugs
- Add features that don't compromise ValdoriaCraft's stability

**Note:** Features specific to other servers may not be accepted if they conflict with ValdoriaCraft's requirements.

## 💬 Support

**For ValdoriaCraft Players/Staff:**
- Contact server administrators directly
- Check ValdoriaCraft's Discord for support

**For Other Servers:**
- This fork is provided as-is without support
- You may use the code and adapt it to your needs
- For the original AxMinions support, visit the official project

## 🎯 ValdoriaCraft

ValdoriaCraft is a premium Minecraft server focused on delivering the best player experience with optimized performance and custom features.

---

**Maintained by:** ValdoriaCraft Development Team  
**Based on:** AxMinions by Artillex Studios  
**Focus:** Performance optimization for large-scale Minecraft servers
