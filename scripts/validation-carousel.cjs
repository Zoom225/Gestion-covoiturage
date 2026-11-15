const assert = require('node:assert/strict');

// Exécuté par validation-ui.cjs, avec les mêmes API simulées et le build de production.
module.exports = async function validateCarousel(page, report, output) {
  const path=require('node:path');
  const index=()=>page.locator('.carousel-dots button').evaluateAll(buttons=>buttons.findIndex(button=>button.getAttribute('aria-current')==='true'));
  const unchanged=async()=>{const before=await index();await page.waitForTimeout(6200);assert.equal(await index(),before);};
  await page.emulateMedia({reducedMotion:'no-preference'});
  await page.setViewportSize({width:1440,height:1000});await page.mouse.move(0,0);
  await page.goto('http://127.0.0.1:4201/',{waitUntil:'networkidle'});
  await page.waitForFunction(()=>document.querySelector('.carousel-photo.active img')?.naturalWidth>0);
  await page.waitForFunction(()=>document.querySelectorAll('.carousel-dots button')[0]?.getAttribute('aria-current')!=='true',{},{timeout:12000});
  report.carousel=['Rotation automatique réelle après six secondes'];
  await page.locator('.carousel-next').click();await unchanged();
  report.carousel.push('Interaction : pause persistante jusqu’à une reprise explicite');
  await page.locator('.carousel-dots button').last().click();
  await page.waitForFunction(()=>document.querySelectorAll('.carousel-dots button')[3]?.getAttribute('aria-current')==='true');
  await page.locator('.carousel-previous').focus();await page.keyboard.press('Home');
  await page.waitForFunction(()=>document.querySelectorAll('.carousel-dots button')[0]?.getAttribute('aria-current')==='true');
  await page.keyboard.press('End');
  await page.waitForFunction(()=>document.querySelectorAll('.carousel-dots button')[3]?.getAttribute('aria-current')==='true');
  await page.keyboard.press('ArrowRight');
  await page.waitForFunction(()=>document.querySelectorAll('.carousel-dots button')[0]?.getAttribute('aria-current')==='true');
  assert.equal(await page.locator('.carousel-previous').evaluate(element=>element===document.activeElement),true);
  report.carousel.push('Flèches, pagination, clavier Début/Fin et focus conservé');
  await page.locator('.carousel-pause').click();
  await page.waitForFunction(()=>document.querySelector('.carousel-pause')?.getAttribute('aria-label')==='Mettre le carrousel en pause');
  const before=await index();await page.waitForFunction(previous=>document.querySelectorAll('.carousel-dots button')[previous]?.getAttribute('aria-current')!=='true',before,{timeout:12000});
  await page.locator('.carousel-pause').click();await unchanged();
  report.carousel.push('Bouton pause puis reprise explicite');
  await page.locator('.carousel-pause').click();
  await page.evaluate(()=>{Object.defineProperty(document,'hidden',{configurable:true,get:()=>true});document.dispatchEvent(new Event('visibilitychange'));});
  await unchanged();
  await page.evaluate(()=>{Reflect.deleteProperty(document,'hidden');document.dispatchEvent(new Event('visibilitychange'));});
  report.carousel.push('Onglet masqué : événement visibilitychange avec état hidden contrôlé');
  await page.emulateMedia({reducedMotion:'reduce'});
  await page.waitForFunction(()=>document.querySelector('.carousel-pause')?.disabled===true);
  await unchanged();
  await page.locator('.carousel-next').click();
  assert.equal(await page.locator('.carousel-photo').first().evaluate(element=>getComputedStyle(element).transitionDuration),'0s');
  report.carousel.push('Mouvement réduit : pas de rotation ni de fondu, navigation manuelle disponible');
  await page.route('**/images/car-*.webp',route=>route.abort());
  await page.reload({waitUntil:'networkidle'});
  await page.waitForFunction(()=>document.querySelector('.carousel-photo.active img')?.getAttribute('src')==='images/mobilite-transport.svg');
  report.carousel.push('Erreur photo : secours SVG local');
  await page.unroute('**/images/car-*.webp');
  await page.emulateMedia({reducedMotion:'no-preference'});
  await page.reload({waitUntil:'networkidle'});
  await page.locator('.carousel-dots button').first().click();
  await page.screenshot({path:path.join(output,'carousel-commandes-1440.jpg'),type:'jpeg',quality:80,fullPage:false});
};

module.exports.validateZoom = async function({chromium, mock, session, report, output}) {
  const fs=require('node:fs'),path=require('node:path');
  const profile=path.resolve('.local-validation',`zoom-carousel-${Date.now()}`);
  fs.mkdirSync(path.join(profile,'Default'),{recursive:true});
  // Zoom natif Chromium, par partition par défaut "x" ; aucun style zoom ni pincement simulé.
  fs.writeFileSync(path.join(profile,'Default','Preferences'),JSON.stringify({partition:{default_zoom_level:{x:Math.log(2)/Math.log(1.2)}}}));
  const context=await chromium.launchPersistentContext(profile,{headless:true,viewport:null,
    executablePath:process.env.EDGE_PATH||'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    args:['--window-size=1440,1000','--force-device-scale-factor=1']});
  try {
    const page=context.pages()[0];await page.route('**/api/**',mock);
    page.on('pageerror',error=>report.errors.push(error.message));
    report.zoom=[];
    for(const [role,url,name] of [
      ['guest','/','accueil'],['guest','/admin/login','connexion-admin'],
      ['voyageur','/voyageur/trajets','recherche'],['voyageur','/voyageur/trajets/new','proposer'],
      ['voyageur','/voyageur/reservations','reservations'],['voyageur','/voyageur/payments','paiements'],
      ['admin','/admin','global'],['local','/admin','agence'],['admin','/admin/voyageurs','voyageurs'],
      ['admin','/admin/vehicules','vehicules'],
    ]) {
      await session(page,role);await page.goto('http://127.0.0.1:4201'+url,{waitUntil:'networkidle'});
      const metrics=await page.evaluate(()=>({dpr:devicePixelRatio,innerWidth,outerWidth,overflow:document.documentElement.scrollWidth>innerWidth+1}));
      assert.ok(Math.abs(metrics.dpr-2)<.05 && metrics.innerWidth<750,'Zoom natif 200 % non effectif');
      assert.equal(metrics.overflow,false,`Debordement avec zoom 200 % : ${name}`);
      report.zoom.push({name,role,...metrics});
      await page.screenshot({path:path.join(output,`zoom200-${name}.jpg`),type:'jpeg',quality:75,fullPage:true});
    }
  }finally{await context.close()}
};
