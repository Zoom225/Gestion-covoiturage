// Outillage facultatif hors application : Playwright + Edge, aucune dépendance Angular ajoutée.
// Les références et conditions d'utilisation sont dans frontend/public/images/LICENCES.md.
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || path.resolve('.local-validation/ui/browser/node_modules/playwright'));
const photos = [
  { name: 'route', id: 28502253, x: .5, y: .66 },
  { name: 'famille', id: 8323226, x: .65, y: .55 },
  { name: 'partage', id: 18378381, x: .5, y: .56 },
  { name: 'bruxelles', id: 14599371, x: .6, y: .65 },
];
const output = path.resolve(process.env.PHOTO_OUTPUT_DIR || 'frontend/public/images');
(async () => {
  fs.mkdirSync(output, { recursive: true });
  const browser = await chromium.launch({ headless: true, executablePath: process.env.EDGE_PATH || 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe' });
  try {
    const page = await browser.newPage();
    for (const photo of photos) {
      const response = await fetch(`https://images.pexels.com/photos/${photo.id}/pexels-photo-${photo.id}.jpeg?auto=compress&cs=tinysrgb&w=1920`);
      if (!response.ok) throw new Error(`Photo ${photo.id}: HTTP ${response.status}`);
      const source = Buffer.from(await response.arrayBuffer()).toString('base64');
      for (const [suffix, width, height] of [['640',640,360], ['1024',1024,576], ['1920',1920,1080], ['mobile',640,800]]) {
        const data = await page.evaluate(async ({ source, photo, width, height }) => {
          const image = new Image(); image.src = `data:image/jpeg;base64,${source}`; await image.decode();
          const ratio = width / height;
          const cropWidth = Math.min(image.width, image.height * ratio);
          const cropHeight = cropWidth / ratio;
          const left = Math.max(0, Math.min(image.width-cropWidth, image.width*photo.x-cropWidth/2));
          const top = Math.max(0, Math.min(image.height-cropHeight, image.height*photo.y-cropHeight/2));
          const canvas = document.createElement('canvas'); canvas.width=width; canvas.height=height;
          const context = canvas.getContext('2d'); context.imageSmoothingQuality='high';
          context.drawImage(image,left,top,cropWidth,cropHeight,0,0,width,height);
          return canvas.toDataURL('image/webp', .8).split(',')[1];
        }, { source, photo, width, height });
        const file = path.join(output,`car-${photo.name}-${suffix}.webp`);
        fs.writeFileSync(file,Buffer.from(data,'base64'));
        console.log(`${path.basename(file)} : ${fs.statSync(file).size} octets`);
      }
    }
  } finally { await browser.close(); }
})().catch(error => { console.error(error.message); process.exitCode=1; });
