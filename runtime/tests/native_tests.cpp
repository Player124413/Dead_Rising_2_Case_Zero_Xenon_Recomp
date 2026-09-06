#include "../gpu/bcn_decode.h"
#include "../android/touch_state.h"
#include "../host/stfs_extract.h"
#include <algorithm>
#include <array>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <filesystem>
#include <fstream>
#include <limits>
#include <string>
#include <thread>
#include <vector>
#define CHECK(x) do { if (!(x)) { std::fprintf(stderr, "FAIL %s:%d: %s\n", __FILE__, __LINE__, #x); std::abort(); } } while (false)
namespace fs = std::filesystem;

void BcnTests() {
    std::vector<uint8_t> p;
    std::array<uint8_t, 16> b{};
    b[0] = 0; b[1] = 0xf8; b[2] = 0xe0; b[3] = 7; // red, green RGB565
    CHECK(Bcn::Decode(Bcn::Format::BC1, b.data(), 8, 4, 4, p));
    CHECK(p.size() == 64 && p[0] == 255 && p[1] == 0 && p[2] == 0 && p[3] == 255);
    b[4] = 0xe4; // row indices 0,1,2,3
    CHECK(Bcn::Decode(Bcn::Format::BC1, b.data(), 8, 4, 4, p));
    CHECK(p[4] == 0 && p[5] == 255 && p[8] == 170 && p[9] == 85 && p[12] == 85 && p[13] == 170);
    b.fill(0); std::fill(b.begin() + 4, b.begin() + 8, 0xff);
    CHECK(Bcn::Decode(Bcn::Format::BC1, b.data(), 8, 4, 4, p));
    CHECK(std::all_of(p.begin(), p.end(), [](uint8_t n){ return n == 0; }));
    b.fill(0xff); b[8] = 0; b[9] = 0xf8; b[10] = 0; b[11] = 0;
    std::fill(b.begin() + 12, b.end(), 0); b[0] = 0xab;
    CHECK(Bcn::Decode(Bcn::Format::BC2, b.data(), 16, 4, 4, p));
    CHECK(p[0] == 255 && p[3] == 187 && p[7] == 170 && p[11] == 255);
    b.fill(0); b[0] = 255; b[1] = 0; b[2] = 2; b[9] = 0xf8;
    CHECK(Bcn::Decode(Bcn::Format::BC3, b.data(), 16, 4, 4, p));
    CHECK(p[0] == 255 && p[3] == 218 && p[7] == 255);
    b.fill(0); b[0] = 10; b[1] = 20; b[2] = 6 | (7 << 3);
    CHECK(Bcn::Decode(Bcn::Format::BC4, b.data(), 8, 4, 4, p));
    CHECK(p[0] == 0 && p[4] == 255 && p[1] == 0 && p[2] == 0 && p[3] == 255);
    b[8] = 77;
    CHECK(Bcn::Decode(Bcn::Format::BC5, b.data(), 16, 4, 4, p));
    CHECK(p[1] == 77 && p[5] == 77 && p[7] == 255);
    // Cropped edge blocks and non-square dimensions.
    b.fill(0); b[1] = 0xf8; b[8] = 31;
    CHECK(Bcn::Decode(Bcn::Format::BC1, b.data(), 16, 5, 3, p));
    CHECK(p.size() == 60 && p[0] == 255 && p[4 * 4 + 2] == 255 && p[14 * 4 + 2] == 255);
    CHECK(!Bcn::Decode(Bcn::Format::BC1, b.data(), 15, 5, 3, p));
    CHECK(!Bcn::Decode(Bcn::Format::BC1, b.data(), 8, 0, 4, p));
    CHECK(!Bcn::Decode(Bcn::Format::BC1, b.data(), 8, UINT32_MAX, 4, p));
    CHECK(!Bcn::Decode(Bcn::Format::BC1, nullptr, 8, 4, 4, p));
    CHECK(!Bcn::Decode(static_cast<Bcn::Format>(99), b.data(), 8, 4, 4, p));
}
void TouchTests() {
    AndroidTouch_Set(0x1000, 2, -2, std::numeric_limits<float>::quiet_NaN(), .5f, 999, -1);
    auto s = AndroidTouch_Read();
    CHECK(s.buttons == 0x1000 && s.thumbLX == 32767 && s.thumbLY == -32767);
    CHECK(s.thumbRX == 0 && s.thumbRY == 16383 && s.leftTrigger == 255 && s.rightTrigger == 0);
    HostPadState pad{}; pad.buttons = 0x2000; pad.thumbRY = -30000; pad.rightTrigger = 15;
    AndroidTouch_Merge(pad);
    CHECK(pad.buttons == 0x3000 && pad.thumbLX == 32767 && pad.thumbRY == -30000 && pad.rightTrigger == 15);
    std::thread a([]{ for(int n=0;n<10000;++n) AndroidTouch_Set(1,.5f,.5f,.5f,.5f,1,1); });
    std::thread b([]{ for(int n=0;n<10000;++n) { auto p=AndroidTouch_Read(); (void)p; AndroidTouch_Clear(); } });
    a.join(); b.join(); AndroidTouch_Clear(); s = AndroidTouch_Read();
    CHECK(s.buttons == 0 && s.thumbLX == 0 && s.thumbRY == 0 && s.leftTrigger == 0);
}
void BE32(std::vector<uint8_t>& p, size_t at, uint32_t n) {
    for (int i=0;i<4;++i) p[at+i] = uint8_t(n >> (24-i*8));
}
std::vector<uint8_t> Package() {
    std::vector<uint8_t> p(0x5000);
    std::copy_n("LIVE", 4, p.begin()); BE32(p,0x340,0x1000); BE32(p,0x360,0x58410A8D);
    p[0x379] = 0x24; p[0x37b] = 1; p[0x37c] = 1; // volume / table count
    BE32(p,0x1014,0xffffff); BE32(p,0x102c,0xffffff);
    std::copy_n("default.xex", 11, p.begin()+0x2000);
    p[0x2028]=11; p[0x202c]=1; p[0x202f]=1; p[0x2032]=p[0x2033]=255; BE32(p,0x2034,4);
    std::copy_n("DATA",4,p.begin()+0x3000);
    return p;
}
void StfsTests(const fs::path& dir) {
    fs::create_directories(dir);
    auto test = [&](std::vector<uint8_t> p, bool want, const char* tag) {
        fs::path input=dir/(std::string(tag)+".stfs"), output=dir/tag;
        { std::ofstream f(input,std::ios::binary); f.write(reinterpret_cast<const char*>(p.data()),p.size()); }
        std::string error; uint64_t last=0;
        bool ok = StfsExtract::Extract(input,output,error,[&](uint64_t n,uint64_t total){CHECK(n>=last && n<=total);last=n;});
        if (ok != want) std::fprintf(stderr,"STFS %s: %s\n",tag,error.c_str());
        CHECK(ok == want);
        if (want) { std::ifstream f(output/"default.xex"); std::string text; f>>text; CHECK(text=="DATA" && last==4); }
    };
    auto p=Package(); test(p,true,"valid");
    BE32(p,0x360,0); test(p,false,"wrong-title");
    p=Package(); p.resize(20); test(p,false,"truncated");
    p=Package(); p[0x2032]=0; p[0x2033]=3; test(p,false,"invalid-parent");
    p=Package(); p[0x2028]=2; p[0x2000]=p[0x2001]='.'; test(p,false,"traversal");
    p=Package(); p[0x2028]=41; test(p,false,"long-name");
    p=Package(); BE32(p,0x2034,UINT32_MAX); test(p,false,"huge-file");
    p=Package(); p[0x37c]=2; BE32(p,0x1014,0); test(p,false,"cyclic-directory");
    p=Package(); std::copy_n(p.begin()+0x2000,64,p.begin()+0x2040); test(p,false,"duplicate");
    p=Package(); BE32(p,0x2034,8192); p[0x202c]=2; BE32(p,0x102c,1); test(p,false,"cyclic-file");
#ifndef _WIN32
    fs::create_directories(dir/"symlink");
    if (!fs::is_symlink(dir/"symlink/default.xex"))
        fs::create_symlink(dir/"outside",dir/"symlink/default.xex");
    test(Package(),false,"symlink"); CHECK(!fs::exists(dir/"outside"));
#endif
}
int main(int argc, char** argv) {
    CHECK(argc==2); BcnTests(); TouchTests(); StfsTests(fs::absolute(argv[1]));
    std::puts("OK: BC1-5 decoding, touch snapshots, bounded STFS extraction");
}
